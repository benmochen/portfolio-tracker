package com.benmochen.portfolio.benchmark;

import com.benmochen.portfolio.TestcontainersConfiguration;
import com.benmochen.portfolio.account.Account;
import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.account.AccountType;
import com.benmochen.portfolio.instrument.Instrument;
import com.benmochen.portfolio.instrument.InstrumentRepository;
import com.benmochen.portfolio.instrument.InstrumentType;
import com.benmochen.portfolio.position.PositionService;
import com.benmochen.portfolio.pricing.Price;
import com.benmochen.portfolio.pricing.PriceId;
import com.benmochen.portfolio.pricing.PriceRepository;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionRepository;
import com.benmochen.portfolio.transaction.TransactionType;
import com.benmochen.portfolio.user.AppUser;
import com.benmochen.portfolio.user.AppUserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures how many database round trips one request costs, and how long it
 * takes, at several data sizes.
 *
 * This exists to produce numbers you can defend, not to assert anything. It
 * prints a table and passes. Run it before a change and after, and the
 * difference is the claim.
 *
 * Deliberately NOT annotated @Transactional. A transactional test would share
 * one persistence context with the code under measurement, so entities loaded
 * during setup would already be cached and the query count would come out
 * lower than it really is. Each service call here opens its own transaction,
 * exactly as a real HTTP request does.
 *
 * The statement count is the honest headline. Wall-clock time on a laptop with
 * a Docker Postgres is noisy and says little about production; the number of
 * round trips is a property of the code.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestcontainersConfiguration.class)
@WithMockUser(username = "benchuser")
class QueryCountBenchmarkIT {

    /** Ledger sizes to measure. Your real account has 237 transactions. */
    private static final int[] LEDGER_SIZES = {237, 1_000, 5_000};

    /** How many instruments the ledger is spread across. Yours has 21. */
    private static final int INSTRUMENT_COUNT = 21;

    /** Timed repetitions per size. The first few runs are discarded. */
    private static final int WARMUP_RUNS = 5;
    private static final int TIMED_RUNS = 30;

    @Autowired private PositionService positionService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PriceRepository priceRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Long userId;

    @BeforeEach
    void seedUser() {
        userId = appUserRepository.findByUsername("benchuser")
                .orElseGet(() -> appUserRepository.save(
                        new AppUser("benchuser", "not-a-real-hash")))
                .getId();
    }

    @AfterEach
    void clearData() {
        // Order matters: children before parents, or foreign keys reject it.
        transactionRepository.deleteAllInBatch();
        priceRepository.deleteAllInBatch();
        instrumentRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
    }

    @Test
    void measurePositionsEndpoint() {
        System.out.println();
        System.out.println("  ledger |  instruments |  queries |  median ms |  p95 ms");
        System.out.println("  -------+--------------+----------+------------+--------");

        for (int size : LEDGER_SIZES) {
            clearData();
            Long accountId = seedLedger(size);

            long queries = countQueriesForOneCall(accountId);

            List<Long> timings = new ArrayList<>();
            for (int i = 0; i < WARMUP_RUNS; i++) {
                positionService.forAccount(accountId, null);
            }
            for (int i = 0; i < TIMED_RUNS; i++) {
                long start = System.nanoTime();
                positionService.forAccount(accountId, null);
                timings.add(System.nanoTime() - start);
            }

            System.out.printf("  %6d |  %11d |  %7d |  %9.1f |  %6.1f%n",
                    size, INSTRUMENT_COUNT, queries,
                    percentile(timings, 50) / 1_000_000.0,
                    percentile(timings, 95) / 1_000_000.0);
        }
        System.out.println();
    }

    /**
     * Counts prepared statements Hibernate sent for exactly one service call.
     *
     * Prepared statements rather than "queries" because that is what Hibernate
     * actually counts, and it includes the lazy loads that do not look like
     * queries in the calling code. Those lazy loads are the whole point: this
     * is how an N+1 becomes visible.
     */
    private long countQueriesForOneCall(Long accountId) {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();

        statistics.clear();
        positionService.forAccount(accountId, null);
        return statistics.getPrepareStatementCount();
    }

    private static long percentile(List<Long> values, int percentile) {
        long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    /**
     * Builds a synthetic ledger: alternating buys and sells across
     * INSTRUMENT_COUNT instruments, with a recent price for each so every
     * position can be valued.
     */
    private Long seedLedger(int transactionCount) {
        Account account = accountRepository.save(
                new Account("bench-" + System.nanoTime(), "Benchmark",
                        AccountType.MARGIN, "CAD", userId));

        List<Instrument> instruments = new ArrayList<>();
        for (int i = 0; i < INSTRUMENT_COUNT; i++) {
            Instrument instrument = new Instrument(
                    "BM" + i, null, "CAD", InstrumentType.EQUITY, "BENCH CORP " + i);
            instrument.setCompanyKey("BENCH CORP " + i);
            instruments.add(instrumentRepository.save(instrument));
        }

        LocalDate today = LocalDate.now();
        List<Price> prices = new ArrayList<>();
        for (Instrument instrument : instruments) {
            prices.add(new Price(
                    new PriceId(instrument.getId(), today.minusDays(1)),
                    new BigDecimal("50.00"), "BENCHMARK"));
        }
        priceRepository.saveAll(prices);

        List<Transaction> ledger = new ArrayList<>(transactionCount);
        LocalDate date = today.minusDays(transactionCount + 1L);

        for (int i = 0; i < transactionCount; i++) {
            Instrument instrument = instruments.get(i % INSTRUMENT_COUNT);
            date = date.plusDays(1);

            // Buy ten, then sell one, so every position stays open and has to
            // be priced. A closed position skips the price lookup and would
            // flatter the query count.
            boolean isBuy = i % 5 != 4;
            BigDecimal quantity = isBuy ? new BigDecimal("10") : new BigDecimal("-1");
            BigDecimal gross = isBuy ? new BigDecimal("500.00") : new BigDecimal("50.00");

            Transaction transaction = new Transaction(
                    account, instrument,
                    isBuy ? TransactionType.BUY : TransactionType.SELL,
                    date, date, quantity, new BigDecimal("50.00"), gross,
                    BigDecimal.ZERO, isBuy ? gross.negate() : gross,
                    "CAD", "benchmark row " + i,
                    uniqueHash(i), (short) 1);
            transaction.setSequenceNo(i);
            ledger.add(transaction);
        }
        transactionRepository.saveAll(ledger);

        return account.getId();
    }

    /** Any distinct value works; the dedupe key only needs to be unique here. */
    private static byte[] uniqueHash(int i) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, (byte) 0);
        hash[0] = (byte) (i & 0xFF);
        hash[1] = (byte) ((i >> 8) & 0xFF);
        hash[2] = (byte) ((i >> 16) & 0xFF);
        return hash;
    }
}
