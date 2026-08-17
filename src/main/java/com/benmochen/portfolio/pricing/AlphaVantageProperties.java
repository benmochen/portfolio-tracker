package com.benmochen.portfolio.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Alpha Vantage settings, bound from application.yml.
 *
 * apiKey is read from an environment variable. It is never written into a
 * config file, because this repository is public: anything committed here is
 * committed forever, including in the git history after you delete it.
 */
@ConfigurationProperties(prefix = "alphavantage")
public record AlphaVantageProperties(
        String apiKey,
        String baseUrl,
        int requestsPerMinute,
        /**
         * "compact" returns the last 100 trading days; "full" returns 20+
         * years but is a paid feature. Kept as a setting so upgrading is one
         * line rather than a code change.
         */
        String outputSize
) {
    public AlphaVantageProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://www.alphavantage.co/query";
        }
        if (requestsPerMinute <= 0) {
            requestsPerMinute = 5;
        }
        if (outputSize == null || outputSize.isBlank()) {
            outputSize = "compact";
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Milliseconds to wait between calls to stay under the per-minute limit. */
    public long throttleMillis() {
        return (60_000L / requestsPerMinute) + 500L;
    }
}
