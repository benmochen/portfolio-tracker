package com.benmochen.portfolio.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One day's exchange rate. rate is how many units of quoteCurrency one unit of
 * baseCurrency buys, so USD/CAD 1.3812 means one US dollar costs 1.3812
 * Canadian dollars.
 */
@Entity
@Table(name = "fx_rate")
public class FxRate {

    @EmbeddedId
    private FxRateId id;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal rate;

    @Column(nullable = false, length = 64)
    private String source;

    protected FxRate() {
    }

    public FxRate(FxRateId id, BigDecimal rate, String source) {
        this.id = id;
        this.rate = rate;
        this.source = source;
    }

    public FxRateId getId() {
        return id;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getSource() {
        return source;
    }
}
