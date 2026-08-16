-- ===========================================================================
-- V4: an instrument is a (symbol, currency) pair
--
-- DLR.TO and DLR.U are the same fund listed in two currencies. Treating them
-- as one instrument pooled a CAD cost with USD proceeds and produced a
-- meaningless realised gain.
--
-- They are separate holdings. Norbert's Gambit is precisely the act of moving
-- units from one to the other, and the broker records the cost that travels
-- with them.
-- ===========================================================================

-- The old key allowed one row per (symbol, exchange), and exchange is always
-- NULL here, which in Postgres makes every row distinct and enforces nothing.
ALTER TABLE instrument
    DROP CONSTRAINT IF EXISTS instrument_symbol_exchange_uq;

ALTER TABLE instrument
    ADD CONSTRAINT instrument_symbol_currency_uq UNIQUE (symbol, currency);

-- Likewise the issuer name is only unique within a currency: one fund can
-- legitimately appear as both a CAD and a USD listing.
DROP INDEX IF EXISTS instrument_company_key_uq;

CREATE UNIQUE INDEX instrument_company_key_currency_uq
    ON instrument (company_key, currency)
    WHERE company_key IS NOT NULL;

-- A raw broker symbol means different instruments depending on the currency
-- of the row it appeared on, so the alias key widens to include it.
ALTER TABLE instrument_alias
    DROP CONSTRAINT instrument_alias_pkey;

ALTER TABLE instrument_alias
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'CAD';

ALTER TABLE instrument_alias
    ALTER COLUMN currency DROP DEFAULT;

ALTER TABLE instrument_alias
    ADD CONSTRAINT instrument_alias_pkey PRIMARY KEY (raw_symbol, currency);
