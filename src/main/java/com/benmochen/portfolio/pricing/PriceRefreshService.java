package com.benmochen.portfolio.pricing;

import com.benmochen.portfolio.instrument.Instrument;
import com.benmochen.portfolio.instrument.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills the price table from the market data provider.
 *
 * Shaped entirely by a 25-request-per-day, 5-per-minute quota:
 *
 *   - it refuses to refetch history it already has, tracked per instrument
 *   - it processes a bounded number of instruments per call, so one run
 *     cannot exhaust the day's allowance
 *   - it sleeps between calls to stay under the per-minute limit
 *
 * A failure on one instrument does not abort the rest: a wrong provider symbol
 * for one holding should not stop the other twenty-one from updating.
 */
@Service
public class PriceRefreshService {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshService.class);

    private final PriceProvider provider;
    private final AlphaVantageProperties properties;
    private final InstrumentRepository instrumentRepository;
    private final PriceRepository priceRepository;

    public PriceRefreshService(PriceProvider provider,
                               AlphaVantageProperties properties,
                               InstrumentRepository instrumentRepository,
                               PriceRepository priceRepository) {
        this.provider = provider;
        this.properties = properties;
        this.instrumentRepository = instrumentRepository;
        this.priceRepository = priceRepository;
    }

    public record RefreshResult(int instrumentsProcessed, int pricesStored,
                                int stillDue, List<String> problems) {
    }

    /**
     * @param heldOnly when true, only instruments with a non-zero holding are
     *                 updated. That is the sensible default: the quota is
     *                 small and a closed position has no market value to show.
     */
    @Transactional
    public RefreshResult refresh(int maxInstruments, boolean heldOnly) {
        LocalDate today = LocalDate.now();
        List<Instrument> due = heldOnly
                ? instrumentRepository.findHeldNeedingPrices(today)
                : instrumentRepository.findNeedingPrices(today);

        List<String> problems = new ArrayList<>();
        int processed = 0;
        int stored = 0;

        for (Instrument instrument : due) {
            if (processed >= maxInstruments) {
                break;
            }
            if (processed > 0) {
                throttle();
            }
            try {
                stored += fetchInto(instrument);
                processed++;
            } catch (RuntimeException e) {
                // Recorded and skipped rather than thrown: one bad provider
                // symbol should not stop every other holding from updating.
                problems.add(instrument.getSymbol() + " (" + instrument.getCurrency() + "): "
                        + e.getMessage());
                processed++;
            }
        }

        int stillDue = Math.max(0, due.size() - processed);
        log.info("Price refresh: {} instruments, {} closes stored, {} problems, {} still due",
                processed, stored, problems.size(), stillDue);
        return new RefreshResult(processed, stored, stillDue, problems);
    }

    private int fetchInto(Instrument instrument) {
        // Re-ask from the day after what is already stored. On a first run
        // this is null, meaning fetch everything.
        LocalDate from = instrument.getPricesFetchedThrough() == null
                ? null
                : instrument.getPricesFetchedThrough().plusDays(1);

        List<DailyClose> closes = provider.fetchDailyCloses(instrument.getProviderSymbol(), from);
        if (closes.isEmpty()) {
            return 0;
        }

        List<Price> rows = closes.stream()
                .map(c -> new Price(
                        new PriceId(instrument.getId(), c.date()),
                        c.close(),
                        provider.name()))
                .toList();

        // saveAll upserts by primary key (instrument_id, price_date), so an
        // overlapping fetch updates rather than duplicating.
        priceRepository.saveAll(rows);

        LocalDate latest = closes.get(closes.size() - 1).date();
        instrument.setPricesFetchedThrough(latest);
        return rows.size();
    }

    /**
     * Waits out the per-minute limit. Thread.sleep is crude but correct here:
     * this runs in a single request thread doing a handful of calls, and a
     * scheduler or rate limiter would be more machinery than the problem needs.
     * A production version would move this to a background job.
     */
    private void throttle() {
        try {
            Thread.sleep(properties.throttleMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PriceFetchException("Interrupted while waiting out the rate limit");
        }
    }
}
