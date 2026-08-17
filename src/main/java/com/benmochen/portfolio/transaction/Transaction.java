package com.benmochen.portfolio.transaction;

import com.benmochen.portfolio.account.Account;
import com.benmochen.portfolio.instrument.Instrument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the append-only ledger.
 *
 * Every monetary field is BigDecimal, never double. Binary floating point
 * cannot represent 0.1 exactly, so double silently accumulates error, and in
 * a system whose whole purpose is computing returns that error is the bug.
 */
@Entity
@Table(name = "account_transaction")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY means the account is not loaded from the database until you call
     * getAccount(). The JPA default for ManyToOne is EAGER, which quietly
     * issues extra queries on every load.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** Null for cash-only rows such as deposits and interest. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id")
    private Instrument instrument;

    @Column(name = "import_batch_id")
    private Long importBatchId;

    /**
     * Position of this row within its trade date, ascending in real time.
     * Assigned at import because the source file may run in either direction
     * and the database id cannot be trusted to reflect chronology.
     */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionType type;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    /** Signed: negative on SELL. Null for cash-only rows. */
    @Column(precision = 20, scale = 8)
    private BigDecimal quantity;

    /** Price per unit, in `currency`. */
    @Column(precision = 20, scale = 8)
    private BigDecimal price;

    @Column(name = "gross_amount", precision = 20, scale = 4)
    private BigDecimal grossAmount;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal commission = BigDecimal.ZERO;

    /** Signed cash impact on the account. Negative on BUY, positive on SELL. */
    @Column(name = "net_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal netAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 1000)
    private String description;

    /** SHA-256 over the meaningful source columns. Drives idempotent import. */
    @Column(name = "row_hash", nullable = false)
    private byte[] rowHash;

    /** Distinguishes genuinely identical rows within one source file. */
    @Column(nullable = false)
    private short occurrence = 1;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }

    public Transaction(Account account, Instrument instrument, TransactionType type,
                       LocalDate tradeDate, LocalDate settlementDate,
                       BigDecimal quantity, BigDecimal price, BigDecimal grossAmount,
                       BigDecimal commission, BigDecimal netAmount, String currency,
                       String description, byte[] rowHash, short occurrence) {
        this.account = account;
        this.instrument = instrument;
        this.type = type;
        this.tradeDate = tradeDate;
        this.settlementDate = settlementDate;
        this.quantity = quantity;
        this.price = price;
        this.grossAmount = grossAmount;
        this.commission = commission == null ? BigDecimal.ZERO : commission;
        this.netAmount = netAmount;
        this.currency = currency;
        this.description = description;
        this.rowHash = rowHash;
        this.occurrence = occurrence;
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Long getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Long importBatchId) {
        this.importBatchId = importBatchId;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public TransactionType getType() {
        return type;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public byte[] getRowHash() {
        return rowHash;
    }

    public short getOccurrence() {
        return occurrence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
