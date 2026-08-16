package com.benmochen.portfolio.instrument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A tradable instrument: a stock, ETF, and so on.
 *
 * Called Instrument rather than Security on purpose: a class or package named
 * Security in a Spring project reads as authentication code to every future
 * reader, including you in three months.
 */
@Entity
@Table(name = "instrument")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    /** e.g. "TSX", "NASDAQ". Nullable because some rows arrive without one. */
    @Column(length = 32)
    private String exchange;

    /** The currency the instrument trades in, which may differ from the account's. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 32)
    private InstrumentType instrumentType;

    @Column
    private String name;

    /**
     * The issuer name taken from the broker row description, normalised.
     * This is what lets an opaque code like "N003056" find the same
     * instrument as the ticker "NVDA": both rows describe NVIDIA CORP.
     */
    @Column(name = "company_key", length = 255)
    private String companyKey;

    /**
     * How the market data provider spells this ticker, which is not how the
     * broker spells it. Stored rather than computed so a wrong mapping is
     * fixed with an UPDATE, not a deploy.
     */
    @Column(name = "provider_symbol", length = 32)
    private String providerSymbol;

    /** Latest date for which closes have been stored. Null means never fetched. */
    @Column(name = "prices_fetched_through")
    private java.time.LocalDate pricesFetchedThrough;

    protected Instrument() {
    }

    public Instrument(String symbol, String exchange, String currency,
                      InstrumentType instrumentType, String name) {
        this.symbol = symbol;
        this.exchange = exchange;
        this.currency = currency;
        this.instrumentType = instrumentType;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getCurrency() {
        return currency;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCompanyKey() {
        return companyKey;
    }

    public void setCompanyKey(String companyKey) {
        this.companyKey = companyKey;
    }

    public String getProviderSymbol() {
        return providerSymbol;
    }

    public void setProviderSymbol(String providerSymbol) {
        this.providerSymbol = providerSymbol;
    }

    public java.time.LocalDate getPricesFetchedThrough() {
        return pricesFetchedThrough;
    }

    public void setPricesFetchedThrough(java.time.LocalDate pricesFetchedThrough) {
        this.pricesFetchedThrough = pricesFetchedThrough;
    }
}
