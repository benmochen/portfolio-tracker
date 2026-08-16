package com.benmochen.portfolio.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Records that a raw symbol string from a broker file means a particular
 * instrument, and how that conclusion was reached.
 *
 * The raw string is the primary key: a given string always means one thing.
 */
@Entity
@Table(name = "instrument_alias")
@jakarta.persistence.IdClass(InstrumentAlias.Key.class)
public class InstrumentAlias {

    /** How the alias was resolved. Stored so a wrong guess can be found later. */
    public enum Source {
        /** The symbol already matched an instrument exactly. */
        EXACT,
        /** Matched after stripping a ".TO" suffix or a leading dot. */
        NORMALISED,
        /** Matched by issuer name taken from the row description. */
        COMPANY_KEY,
        /** Entered by hand. */
        MANUAL,
        /** Nothing matched; a placeholder instrument was created. */
        UNRESOLVED
    }

    @Id
    @Column(name = "raw_symbol", length = 64)
    private String rawSymbol;

    /**
     * Part of the key: the same raw symbol means a different instrument
     * depending on the currency of the row it appeared on.
     */
    @Id
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Source source;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Composite key holder required by JPA for a two-column primary key. */
    public static class Key implements java.io.Serializable {
        private String rawSymbol;
        private String currency;

        public Key() {
        }

        public Key(String rawSymbol, String currency) {
            this.rawSymbol = rawSymbol;
            this.currency = currency;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key other)) {
                return false;
            }
            return java.util.Objects.equals(rawSymbol, other.rawSymbol)
                    && java.util.Objects.equals(currency, other.currency);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(rawSymbol, currency);
        }
    }

    protected InstrumentAlias() {
    }

    public InstrumentAlias(String rawSymbol, String currency, Long instrumentId, Source source) {
        this.rawSymbol = rawSymbol;
        this.currency = currency;
        this.instrumentId = instrumentId;
        this.source = source;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRawSymbol() {
        return rawSymbol;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public Source getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
