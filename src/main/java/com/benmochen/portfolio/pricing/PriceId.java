package com.benmochen.portfolio.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Composite primary key for the price table: (instrument_id, price_date).
 *
 * JPA requires a key class to be Serializable and to implement equals and
 * hashCode, because it uses them to identify entities inside the persistence
 * context.
 */
@Embeddable
public class PriceId implements Serializable {

    @Column(name = "instrument_id")
    private Long instrumentId;

    @Column(name = "price_date")
    private LocalDate priceDate;

    protected PriceId() {
    }

    public PriceId(Long instrumentId, LocalDate priceDate) {
        this.instrumentId = instrumentId;
        this.priceDate = priceDate;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PriceId other)) {
            return false;
        }
        return Objects.equals(instrumentId, other.instrumentId)
                && Objects.equals(priceDate, other.priceDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentId, priceDate);
    }
}
