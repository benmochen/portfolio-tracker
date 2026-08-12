package com.benmochen.portfolio.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/** A brokerage account. One row per Questrade account. */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Questrade account number. Unique across all accounts. */
    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    /** Your own label, e.g. "TFSA main". */
    @Column(nullable = false)
    private String name;

    /**
     * EnumType.STRING stores the name ("TFSA"), not the ordinal position.
     * Ordinals are a trap: reordering the enum silently rewrites the meaning
     * of every existing row.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType accountType;

    /** ISO 4217 code, e.g. "CAD". Stored as CHAR(3). */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Filled by the database DEFAULT now(), never by Java. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. Not for application use. */
    protected Account() {
    }

    public Account(String externalId, String name, AccountType accountType, String currency) {
        this.externalId = externalId;
        this.name = name;
        this.accountType = accountType;
        this.currency = currency;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
