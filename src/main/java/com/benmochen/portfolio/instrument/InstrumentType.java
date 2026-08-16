package com.benmochen.portfolio.instrument;

/** Mirrors the instrument_type CHECK constraint in V1__initial_schema.sql. */
public enum InstrumentType {
    EQUITY,
    ETF,
    MUTUAL_FUND,
    OPTION,
    BOND,
    CASH
}
