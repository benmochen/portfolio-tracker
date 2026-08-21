package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.account.Account;
import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.security.CurrentUser;
import com.benmochen.portfolio.account.AccountType;
import com.benmochen.portfolio.instrument.Instrument;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionRepository;
import com.benmochen.portfolio.transaction.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a Questrade Activities export into the ledger.
 *
 * The whole import runs in one transaction: either every row lands or none
 * does. A partial import is worse than no import, because you cannot tell by
 * looking which half you have.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final QuestradeWorkbookReader reader;
    private final QuestradeActionMapper actionMapper;
    private final RowHasher rowHasher;
    private final AccountRepository accountRepository;
    private final SymbolResolver symbolResolver;
    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository importBatchRepository;
    private final CurrentUser currentUser;

    public ImportService(QuestradeWorkbookReader reader,
                         QuestradeActionMapper actionMapper,
                         RowHasher rowHasher,
                         AccountRepository accountRepository,
                         SymbolResolver symbolResolver,
                         TransactionRepository transactionRepository,
                         ImportBatchRepository importBatchRepository,
                         CurrentUser currentUser) {
        this.reader = reader;
        this.actionMapper = actionMapper;
        this.rowHasher = rowHasher;
        this.accountRepository = accountRepository;
        this.symbolResolver = symbolResolver;
        this.transactionRepository = transactionRepository;
        this.importBatchRepository = importBatchRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public ImportDtos.ImportResult importWorkbook(String filename, byte[] fileBytes) {
        byte[] fileHash = rowHasher.digest(fileBytes);

        // Cheapest possible duplicate check: identical bytes means identical
        // content, so there is nothing to do. Overlapping-but-different
        // exports still get here and are deduplicated row by row below.
        var existing = importBatchRepository.findByFileHash(fileHash);
        if (existing.isPresent()) {
            ImportBatch batch = existing.get();
            return new ImportDtos.ImportResult(batch.getId(), batch.getRowCount(),
                    0, batch.getRowCount(), true, List.of());
        }

        List<ActivityRow> rows;
        try (var in = new java.io.ByteArrayInputStream(fileBytes)) {
            rows = reader.read(in);
        } catch (IOException e) {
            throw new ImportException("Could not read the uploaded file: " + e.getMessage(), e);
        }

        ImportBatch batch = importBatchRepository.save(
                new ImportBatch(filename, fileHash, rows.size()));

        List<String> warnings = new java.util.ArrayList<>();

        // Questrade exports newest activity first. Cost basis is
        // path-dependent, so the ledger needs a real chronological order, and
        // reversing the list here is what gives every later calculation a
        // correct one. Detected from the data rather than assumed, because a
        // different export setting could produce the opposite direction.
        if (isNewestFirst(rows)) {
            rows = new java.util.ArrayList<>(rows);
            java.util.Collections.reverse(rows);
        }

        // First pass: register every instrument that has a real ticker, so
        // opaque internal codes can be matched by issuer name in the second
        // pass regardless of where they sit in the file.
        symbolResolver.prime(rows, warnings);

        // Counts identical rows inside this one file, so two genuinely
        // separate but byte-identical trades both survive.
        Map<String, Short> occurrenceWithinFile = new HashMap<>();

        int inserted = 0;
        int skipped = 0;
        int sequenceNo = 0;

        for (ActivityRow row : rows) {
            byte[] hash = rowHasher.hash(row);
            String hashKey = java.util.HexFormat.of().formatHex(hash);

            short alreadyStored = transactionRepository.maxOccurrenceForHash(hash);
            short seenInThisFile = occurrenceWithinFile.merge(hashKey, (short) 1,
                    (a, b) -> (short) (a + b));

            if (seenInThisFile <= alreadyStored) {
                // This exact row, at this occurrence number, is already in the
                // ledger from a previous overlapping export.
                skipped++;
                continue;
            }

            Transaction transaction = toTransaction(row, hash, seenInThisFile, warnings);
            transaction.setImportBatchId(batch.getId());
            // Rows now arrive oldest-first, so the running index is itself a
            // chronological sequence. Stored per row so ordering survives
            // future imports that may interleave with these.
            transaction.setSequenceNo(sequenceNo++);
            transactionRepository.save(transaction);
            inserted++;
        }

        batch.setInsertedCount(inserted);
        batch.setSkippedCount(skipped);

        log.info("Imported {}: {} rows, {} inserted, {} skipped as duplicates",
                filename, rows.size(), inserted, skipped);

        return new ImportDtos.ImportResult(batch.getId(), rows.size(), inserted, skipped,
                false, List.copyOf(warnings));
    }

    /**
     * True when the file lists recent activity before older activity.
     * Compares the first and last dated rows rather than trusting a
     * hard-coded assumption about the broker's export format.
     */
    private boolean isNewestFirst(List<ActivityRow> rows) {
        if (rows.size() < 2) {
            return false;
        }
        var first = rows.get(0).transactionDate();
        var last = rows.get(rows.size() - 1).transactionDate();
        return first != null && last != null && first.isAfter(last);
    }

    private Transaction toTransaction(ActivityRow row, byte[] hash, short occurrence,
                                      List<String> warnings) {
        TransactionType type = actionMapper.map(row);
        Account account = resolveAccount(row);

        // Resolution, not plain lookup: the same holding appears under
        // several different symbol strings in one file.
        Instrument instrument = symbolResolver.resolve(
                row.symbol(), row.description(), row.currency(), warnings);
        if (type.requiresInstrument() && instrument == null) {
            throw new ImportException(
                    "Row on " + row.transactionDate() + " maps to " + type
                    + " but has no symbol, which the schema does not allow.");
        }

        return new Transaction(
                account,
                instrument,
                type,
                row.transactionDate(),
                row.settlementDate(),
                row.quantity(),
                row.price(),
                row.grossAmount(),
                normaliseCommission(row.commission()),
                row.netAmount(),
                row.currency(),
                row.description(),
                hash,
                occurrence);
    }

    /**
     * Questrade reports commission with an inconsistent sign: most rows are
     * positive, a minority negative for the same kind of charge. Storing a
     * consistent sign here means downstream cost-basis code does not have to
     * guess. The signed cash effect is already carried by netAmount.
     */
    private BigDecimal normaliseCommission(BigDecimal commission) {
        return commission == null ? BigDecimal.ZERO : commission.abs();
    }

    /**
     * Accounts are created on first sight rather than requiring you to set
     * them up by hand, because the export already carries both the account
     * number and its type.
     */
    private Account resolveAccount(ActivityRow row) {
        String externalId = row.accountNumber();
        Long userId = currentUser.requireId();
        // Scoped by user: two people can legitimately hold accounts at the
        // same broker, and an unscoped lookup would attach one person's
        // transactions to the other's account.
        return accountRepository.findByExternalIdAndUserId(externalId, userId)
                .orElseGet(() -> accountRepository.save(new Account(
                        externalId,
                        row.accountTypeLabel() == null ? externalId : row.accountTypeLabel(),
                        mapAccountType(row.accountTypeLabel()),
                        "CAD",
                        userId)));
    }

    /**
     * Questrade writes labels like "Individual TFSA" and "Individual FHSA",
     * so the match is on a contained keyword rather than equality.
     */
    private AccountType mapAccountType(String label) {
        String value = label == null ? "" : label.toUpperCase(Locale.ROOT);
        if (value.contains("TFSA")) {
            return AccountType.TFSA;
        }
        if (value.contains("FHSA")) {
            return AccountType.FHSA;
        }
        if (value.contains("RRSP") || value.contains("RSP")) {
            return AccountType.RRSP;
        }
        if (value.contains("RESP")) {
            return AccountType.RESP;
        }
        if (value.contains("LIRA")) {
            return AccountType.LIRA;
        }
        if (value.contains("MARGIN")) {
            return AccountType.MARGIN;
        }
        return AccountType.CASH;
    }
}
