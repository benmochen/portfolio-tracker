package com.benmochen.portfolio.transaction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Mirrors the type CHECK constraint in V1__initial_schema.sql.
 */
public enum TransactionType {
    BUY,
    SELL,
    DIVIDEND,
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    INTEREST,
    TAX,
    FX_CONVERSION,
    TRANSFER_IN,
    TRANSFER_OUT,
    SPLIT_ADJUSTMENT;

    /**
     * Types that must reference an instrument. Kept in sync with the
     * account_transaction_instrument_presence_ck constraint.
     */
    private static final Set<TransactionType> INSTRUMENT_REQUIRED =
            EnumSet.of(BUY, SELL, DIVIDEND, SPLIT_ADJUSTMENT);

    public boolean requiresInstrument() {
        return INSTRUMENT_REQUIRED.contains(this);
    }

    /** True for types that move cash into or out of the account itself. */
    public boolean isExternalCashFlow() {
        return this == DEPOSIT || this == WITHDRAWAL
                || this == TRANSFER_IN || this == TRANSFER_OUT;
    }
}
