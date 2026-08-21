package com.benmochen.portfolio.position;

import com.benmochen.portfolio.common.NotFoundException;
import com.benmochen.portfolio.security.CurrentUser;
import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.pricing.Price;
import com.benmochen.portfolio.pricing.PriceRepository;
import com.benmochen.portfolio.position.PositionDtos.PositionResponse;
import com.benmochen.portfolio.position.PositionDtos.PositionsResponse;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PositionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PositionCalculator calculator;
    private final PriceRepository priceRepository;
    private final CurrentUser currentUser;

    public PositionService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           PositionCalculator calculator,
                           PriceRepository priceRepository,
                           CurrentUser currentUser) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.calculator = calculator;
        this.priceRepository = priceRepository;
        this.currentUser = currentUser;
    }

    /**
     * @param asOf optional cut-off. Because positions are derived rather than
     *             stored, asking what you held on any past date costs nothing
     *             extra: replay the ledger up to that date and stop.
     */
    @Transactional(readOnly = true)
    public PositionsResponse forAccount(Long accountId, LocalDate asOf) {
        // Ownership check before any data is read. Without it, authentication
        // alone would let any logged-in user read any account by id.
        if (!accountRepository.existsByIdAndUserId(accountId, currentUser.requireId())) {
            throw NotFoundException.of("Account", accountId);
        }

        List<Transaction> ledger = asOf == null
                ? transactionRepository.findLedger(accountId)
                : transactionRepository.findLedgerAsOf(accountId, asOf);

        var positions = calculator.calculate(ledger).values();

        // Valued at the last close on or before the cut-off, not simply the
        // latest close: asking what a portfolio was worth in January must not
        // silently use today's prices.
        LocalDate valuationDate = asOf == null ? LocalDate.now() : asOf;

        // One query for every open position's price, not one query each.
        // Previously this loop issued a SELECT per position; measured at 21
        // extra round trips on a real account.
        List<Long> openInstrumentIds = positions.stream()
                .filter(Position::isOpen)
                .map(Position::getInstrumentId)
                .toList();

        Map<Long, Price> pricesByInstrument = openInstrumentIds.isEmpty()
                ? Map.of()
                : priceRepository
                    .findLatestOnOrBeforeForAll(openInstrumentIds, valuationDate)
                    .stream()
                    .collect(Collectors.toMap(
                            price -> price.getId().getInstrumentId(),
                            price -> price));

        List<PositionResponse> open = positions.stream()
                .filter(Position::isOpen)
                .map(p -> {
                    Price price = pricesByInstrument.get(p.getInstrumentId());
                    return price == null
                            ? PositionResponse.from(p)
                            : PositionResponse.from(p, price.getClose(),
                                    price.getId().getPriceDate());
                })
                .sorted(Comparator.comparing(PositionResponse::symbol))
                .toList();

        // Closed positions are kept rather than dropped: their realised gains
        // and dividends are part of the account's history even though the
        // holding is gone.
        List<PositionResponse> closed = positions.stream()
                .filter(p -> !p.isOpen())
                .map(PositionResponse::from)
                .sorted(Comparator.comparing(PositionResponse::symbol))
                .toList();

        return new PositionsResponse(accountId, asOf, open, closed);
    }
}
