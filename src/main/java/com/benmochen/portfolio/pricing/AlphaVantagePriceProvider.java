package com.benmochen.portfolio.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Alpha Vantage implementation of PriceProvider.
 *
 * Chosen because it is the only free tier covering both US listings and the
 * Toronto Stock Exchange, which this portfolio needs. The cost is a tight
 * quota: 25 requests per day at 5 per minute.
 *
 * One call returns a whole block of history rather than a date range, because
 * with 25 requests a day, fetching day by day would spend the entire allowance
 * on a single holding.
 *
 * On the free tier that block is the last 100 trading days: outputsize=full,
 * which returns 20+ years, became a paid feature. 100 days is enough to value
 * a portfolio today and to keep it current from here on, but it cannot
 * reconstruct what a holding was worth two years ago.
 *
 * Responses are read as plain Maps rather than through Jackson's JsonNode.
 * Spring Boot 4 ships Jackson 3, which moved every class from the
 * com.fasterxml.jackson package to tools.jackson, so JsonNode code written
 * against any older example fails to compile. Maps sidestep the question.
 */
@Component
public class AlphaVantagePriceProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantagePriceProvider.class);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final AlphaVantageProperties properties;

    public AlphaVantagePriceProvider(AlphaVantageProperties properties) {
        this.properties = properties;
        // Built directly rather than injected. Spring Boot 4 split its
        // starters more finely, so a RestClient.Builder bean is not
        // auto-configured by spring-boot-starter-web alone. This class needs
        // one plain HTTP client against one base URL, so creating it here is
        // both simpler and one less thing to wire.
        this.restClient = RestClient.create(properties.baseUrl());
    }

    @Override
    public String name() {
        return "ALPHAVANTAGE";
    }

    @Override
    public List<DailyClose> fetchDailyCloses(String providerSymbol, LocalDate from) {
        requireKey();

        Map<String, Object> body = restClient.get()
                .uri(uri -> uri
                        .queryParam("function", "TIME_SERIES_DAILY")
                        .queryParam("symbol", providerSymbol)
                        .queryParam("outputsize", properties.outputSize())
                        .queryParam("apikey", properties.apiKey())
                        .build())
                .retrieve()
                .body(JSON_OBJECT);

        checkForApiError(body, providerSymbol);

        Object seriesRaw = body.get("Time Series (Daily)");
        if (!(seriesRaw instanceof Map<?, ?> series)) {
            throw new PriceFetchException(
                    "No daily series returned for '" + providerSymbol
                    + "'. The provider symbol is probably wrong. "
                    + "Try /api/prices/search to find the right one.");
        }

        List<DailyClose> closes = new ArrayList<>();
        for (Map.Entry<?, ?> entry : series.entrySet()) {
            LocalDate date = LocalDate.parse(String.valueOf(entry.getKey()));
            if (from != null && date.isBefore(from)) {
                continue;
            }
            if (!(entry.getValue() instanceof Map<?, ?> fields)) {
                continue;
            }
            Object close = fields.get("4. close");
            if (close == null) {
                continue;
            }
            closes.add(new DailyClose(date, new BigDecimal(String.valueOf(close))));
        }
        closes.sort(Comparator.comparing(DailyClose::date));

        log.info("Fetched {} closes for {}", closes.size(), providerSymbol);
        return closes;
    }

    @Override
    public List<String> searchSymbols(String query) {
        requireKey();

        Map<String, Object> body = restClient.get()
                .uri(uri -> uri
                        .queryParam("function", "SYMBOL_SEARCH")
                        .queryParam("keywords", query)
                        .queryParam("apikey", properties.apiKey())
                        .build())
                .retrieve()
                .body(JSON_OBJECT);

        checkForApiError(body, query);

        if (!(body.get("bestMatches") instanceof List<?> matches)) {
            return List.of();
        }
        List<String> results = new ArrayList<>();
        for (Object match : matches) {
            if (match instanceof Map<?, ?> fields) {
                results.add(text(fields, "1. symbol") + "  |  " + text(fields, "2. name")
                        + "  |  " + text(fields, "4. region")
                        + "  |  " + text(fields, "8. currency"));
            }
        }
        return results;
    }

    private static String text(Map<?, ?> fields, String key) {
        Object value = fields.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private void requireKey() {
        if (!properties.isConfigured()) {
            throw new PriceFetchException(
                    "No Alpha Vantage API key. Set the ALPHAVANTAGE_API_KEY "
                    + "environment variable in the terminal tab that starts the app.");
        }
    }

    /**
     * Alpha Vantage answers with HTTP 200 even when it refuses the request,
     * putting the reason in a "Note", "Information" or "Error Message" field.
     * Checking the status code alone would read a quota refusal as an empty
     * but successful result, silently leaving prices stale.
     */
    private void checkForApiError(Map<String, Object> body, String context) {
        if (body == null || body.isEmpty()) {
            throw new PriceFetchException("Empty response from Alpha Vantage for " + context);
        }
        for (String field : List.of("Note", "Information", "Error Message")) {
            Object value = body.get(field);
            if (value != null) {
                throw new PriceFetchException(
                        "Alpha Vantage refused the request for " + context + ": " + value);
            }
        }
    }
}
