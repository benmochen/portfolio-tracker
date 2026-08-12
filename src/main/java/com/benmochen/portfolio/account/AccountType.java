package com.benmochen.portfolio.account;

/**
 * Mirrors the account_type CHECK constraint in V1__initial_schema.sql.
 * Adding a value here also requires a new migration to widen that constraint.
 */
public enum AccountType {
    TFSA,
    RRSP,
    FHSA,
    MARGIN,
    CASH,
    RESP,
    LIRA
}
