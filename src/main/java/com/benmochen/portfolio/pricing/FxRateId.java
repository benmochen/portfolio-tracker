package com.benmochen.portfolio.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Composite key: one rate per currency pair per day. */
@Embeddable
public class FxRateId implements Serializable {

    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", length = 3)
    private String quoteCurrency;

    @Column(name = "rate_date")
    private LocalDate rateDate;

    protected FxRateId() {
    }

    public FxRateId(String baseCurrency, String quoteCurrency, LocalDate rateDate) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
        this.rateDate = rateDate;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FxRateId other)) {
            return false;
        }
        return Objects.equals(baseCurrency, other.baseCurrency)
                && Objects.equals(quoteCurrency, other.quoteCurrency)
                && Objects.equals(rateDate, other.rateDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency, quoteCurrency, rateDate);
    }
}
