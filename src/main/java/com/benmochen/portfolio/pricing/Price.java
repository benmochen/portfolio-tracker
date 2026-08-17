package com.benmochen.portfolio.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Daily closing price for one instrument. */
@Entity
@Table(name = "price")
public class Price {

    @EmbeddedId
    private PriceId id;

    @Column(name = "close", nullable = false, precision = 20, scale = 8)
    private BigDecimal close;

    /** Which provider this came from, so bad data can be traced and refetched. */
    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "fetched_at", nullable = false, insertable = false, updatable = false)
    private Instant fetchedAt;

    protected Price() {
    }

    public Price(PriceId id, BigDecimal close, String source) {
        this.id = id;
        this.close = close;
        this.source = source;
    }

    public PriceId getId() {
        return id;
    }

    public BigDecimal getClose() {
        return close;
    }

    public String getSource() {
        return source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
