package com.benmochen.portfolio.scheduling;

import com.benmochen.portfolio.pricing.AlphaVantageProperties;
import com.benmochen.portfolio.pricing.FxRateService;
import com.benmochen.portfolio.pricing.PriceRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps market data current without anyone running a command.
 *
 * Disabled by default, and switched on with scheduling.enabled=true. A
 * developer starting the app to poke at one endpoint should not silently spend
 * the day's API quota, and two instances both running the schedule would spend
 * it twice.
 */
@Component
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true")
public class MarketDataScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataScheduler.class);

    /** Bounded so one run cannot exhaust the daily allowance. */
    private static final int MAX_INSTRUMENTS_PER_RUN = 20;

    private final PriceRefreshService priceRefreshService;
    private final FxRateService fxRateService;
    private final AlphaVantageProperties alphaVantageProperties;

    public MarketDataScheduler(PriceRefreshService priceRefreshService,
                               FxRateService fxRateService,
                               AlphaVantageProperties alphaVantageProperties) {
        this.priceRefreshService = priceRefreshService;
        this.fxRateService = fxRateService;
        this.alphaVantageProperties = alphaVantageProperties;
    }

    /**
     * Runs on weekday evenings, after North American markets have closed and
     * the day's closing prices exist. Running in the morning would repeatedly
     * fetch the previous day and waste the quota.
     *
     * Only holdings you still own are refreshed, which is what keeps 22
     * instruments inside a 25-request allowance.
     */
    @Scheduled(cron = "0 30 18 * * MON-FRI", zone = "America/Toronto")
    public void refreshPrices() {
        if (!alphaVantageProperties.isConfigured()) {
            log.warn("Skipping scheduled price refresh: no API key configured");
            return;
        }
        try {
            var result = priceRefreshService.refresh(MAX_INSTRUMENTS_PER_RUN, true);
            log.info("Scheduled price refresh: {} instruments, {} closes, {} still due",
                    result.instrumentsProcessed(), result.pricesStored(), result.stillDue());
            if (!result.problems().isEmpty()) {
                log.warn("Scheduled price refresh had {} problems: {}",
                        result.problems().size(), result.problems());
            }
        } catch (RuntimeException e) {
            // Swallowed on purpose, and logged at error so it is visible: an
            // exception escaping a scheduled method cancels all FUTURE runs of
            // that schedule in Spring's default scheduler. One bad night must
            // not silently stop the job forever.
            log.error("Scheduled price refresh failed", e);
        }
    }

    /**
     * Exchange rates come from the Bank of Canada, which has no quota, so this
     * can run earlier and more freely than the price job.
     */
    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "America/Toronto")
    public void refreshFxRates() {
        try {
            var result = fxRateService.refresh(null);
            log.info("Scheduled FX refresh: {} rates through {}",
                    result.ratesStored(), result.through());
        } catch (RuntimeException e) {
            log.error("Scheduled FX refresh failed", e);
        }
    }
}
