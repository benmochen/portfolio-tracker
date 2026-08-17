package com.benmochen.portfolio.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * USD to CAD rates from the Bank of Canada's Valet API.
 *
 * Chosen over the market data provider deliberately: it is free, needs no API
 * key, imposes no quota, publishes full history rather than a 100-day window,
 * and is the authoritative source for Canadian rates. The one constraint that
 * mattered for prices simply does not apply here.
 */
@Service
public class FxRateService {

    private static final Logger log = LoggerFactory.getLogger(FxRateService.class);

    private static final String SOURCE = "BANK_OF_CANADA";
    private static final String SERIES = "FXUSDCAD";
    private static final String BASE_URL = "https://www.bankofcanada.ca/valet";

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient = RestClient.create(BASE_URL);
    private final FxRateRepository fxRateRepository;

    public FxRateService(FxRateRepository fxRateRepository) {
        this.fxRateRepository = fxRateRepository;
    }

    public record FxRefreshResult(int ratesStored, LocalDate through) {
    }

    @Transactional
    public FxRefreshResult refresh(LocalDate from) {
        LocalDate start = from != null
                ? from
                : fxRateRepository.findLatestDate("USD", "CAD")
                        .map(d -> d.plusDays(1))
                        // Far enough back to cover any plausible ledger.
                        .orElse(LocalDate.of(2020, 1, 1));

        Map<String, Object> body = restClient.get()
                .uri(uri -> uri
                        .path("/observations/{series}/json")
                        .queryParam("start_date", start.toString())
                        .build(SERIES))
                .retrieve()
                .body(JSON_OBJECT);

        if (body == null || !(body.get("observations") instanceof List<?> observations)) {
            throw new PriceFetchException("Bank of Canada returned no observations");
        }

        List<FxRate> rows = new ArrayList<>();
        LocalDate latest = null;

        for (Object observation : observations) {
            if (!(observation instanceof Map<?, ?> fields)) {
                continue;
            }
            Object date = fields.get("d");
            Object series = fields.get(SERIES);
            if (date == null || !(series instanceof Map<?, ?> valueField)) {
                continue;
            }
            Object value = valueField.get("v");
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            LocalDate rateDate = LocalDate.parse(String.valueOf(date));
            rows.add(new FxRate(
                    new FxRateId("USD", "CAD", rateDate),
                    new BigDecimal(String.valueOf(value)),
                    SOURCE));
            if (latest == null || rateDate.isAfter(latest)) {
                latest = rateDate;
            }
        }

        fxRateRepository.saveAll(rows);
        log.info("Stored {} USD/CAD rates through {}", rows.size(), latest);
        return new FxRefreshResult(rows.size(), latest);
    }

    /**
     * Converts an amount between CAD and USD using the rate on or before the
     * given date.
     *
     * Rates are looked up historically rather than using today's, because a
     * purchase made in 2024 was made at 2024's exchange rate. Using the
     * current rate everywhere would silently fold currency movement into what
     * looks like investment performance.
     */
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String from, String to, LocalDate asOf) {
        if (amount == null || from.equals(to)) {
            return amount;
        }
        FxRate usdCad = fxRateRepository.findLatestOnOrBefore("USD", "CAD", asOf)
                .orElseThrow(() -> new PriceFetchException(
                        "No USD/CAD rate on or before " + asOf
                        + ". Run POST /api/fx/refresh first."));

        if ("USD".equals(from) && "CAD".equals(to)) {
            return amount.multiply(usdCad.getRate(), MC);
        }
        if ("CAD".equals(from) && "USD".equals(to)) {
            return amount.divide(usdCad.getRate(), MC);
        }
        throw new PriceFetchException("Unsupported conversion " + from + " to " + to);
    }
}
