-- ===========================================================================
-- Portfolio tracker: initial schema
--
-- Core design rule: the transaction ledger is the single source of truth.
-- Positions, cost basis and returns are always DERIVED by replaying the
-- ledger. Nothing stores "you currently own 47 shares" as mutable state.
-- That is what makes "what did I hold on 2024-03-03?" answerable.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- account: one row per brokerage account (Questrade TFSA, RRSP, margin, ...)
-- ---------------------------------------------------------------------------
CREATE TABLE account (
    id            BIGSERIAL   PRIMARY KEY,
    external_id   VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    account_type  VARCHAR(32)  NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT account_external_id_uq UNIQUE (external_id),
    CONSTRAINT account_type_ck CHECK (
        account_type IN ('TFSA','RRSP','FHSA','MARGIN','CASH','RESP','LIRA')
    )
);

-- ---------------------------------------------------------------------------
-- instrument: a tradable thing (stock, ETF, ...).
--
-- Named "instrument" rather than "security" on purpose: in a Spring project a
-- package or class called Security collides conceptually with Spring Security
-- and will confuse every future reader.
-- ---------------------------------------------------------------------------
CREATE TABLE instrument (
    id               BIGSERIAL PRIMARY KEY,
    symbol           VARCHAR(32)  NOT NULL,
    exchange         VARCHAR(32),
    currency         VARCHAR(3)   NOT NULL,
    instrument_type  VARCHAR(32)  NOT NULL,
    name             VARCHAR(255),

    CONSTRAINT instrument_symbol_exchange_uq UNIQUE (symbol, exchange),
    CONSTRAINT instrument_type_ck CHECK (
        instrument_type IN ('EQUITY','ETF','MUTUAL_FUND','OPTION','BOND','CASH')
    )
);

-- ---------------------------------------------------------------------------
-- import_batch: one row per uploaded Questrade CSV.
--
-- file_hash makes re-uploading the byte-identical file a no-op. The harder
-- case (two exports whose date ranges overlap) is handled per-row below.
-- ---------------------------------------------------------------------------
CREATE TABLE import_batch (
    id           BIGSERIAL   PRIMARY KEY,
    account_id   BIGINT      REFERENCES account (id),
    filename     VARCHAR(255),
    file_hash    BYTEA       NOT NULL,
    row_count    INTEGER     NOT NULL,
    inserted_count INTEGER   NOT NULL DEFAULT 0,
    skipped_count  INTEGER   NOT NULL DEFAULT 0,
    imported_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT import_batch_file_hash_uq UNIQUE (file_hash)
);

-- ---------------------------------------------------------------------------
-- account_transaction: the append-only ledger.
--
-- Table is NOT called "transaction" because that is a SQL keyword and makes
-- every hand-written query need quoting. Every row belongs to an account, so
-- account_transaction is both safe and accurate.
--
-- Questrade's activity export has no per-row unique id, so idempotent import
-- relies on row_hash: a SHA-256 digest over the meaningful source columns.
-- Two genuinely distinct trades can be byte-identical (two separate 10-share
-- buys of the same symbol at the same price on the same day), so the unique
-- key is (row_hash, occurrence), where occurrence counts identical rows within
-- one source file. Re-importing an overlapping range then dedupes correctly
-- without collapsing real duplicates.
-- ---------------------------------------------------------------------------
CREATE TABLE account_transaction (
    id               BIGSERIAL      PRIMARY KEY,
    account_id       BIGINT         NOT NULL REFERENCES account (id),
    instrument_id    BIGINT         REFERENCES instrument (id),
    import_batch_id  BIGINT         REFERENCES import_batch (id),

    type             VARCHAR(32)    NOT NULL,
    trade_date       DATE           NOT NULL,
    settlement_date  DATE,

    quantity         NUMERIC(20,8),
    price            NUMERIC(20,8),
    gross_amount     NUMERIC(20,4),
    commission       NUMERIC(20,4)  NOT NULL DEFAULT 0,
    net_amount       NUMERIC(20,4)  NOT NULL,
    currency         VARCHAR(3)     NOT NULL,

    description      VARCHAR(1000),
    row_hash         BYTEA          NOT NULL,
    occurrence       SMALLINT       NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT account_transaction_dedupe_uq UNIQUE (row_hash, occurrence),
    CONSTRAINT account_transaction_type_ck CHECK (
        type IN ('BUY','SELL','DIVIDEND','DEPOSIT','WITHDRAWAL','FEE',
                 'INTEREST','TAX','FX_CONVERSION','TRANSFER_IN',
                 'TRANSFER_OUT','SPLIT_ADJUSTMENT')
    ),
    CONSTRAINT account_transaction_instrument_presence_ck CHECK (
        (type IN ('BUY','SELL','DIVIDEND','SPLIT_ADJUSTMENT') AND instrument_id IS NOT NULL)
        OR
        (type NOT IN ('BUY','SELL','DIVIDEND','SPLIT_ADJUSTMENT'))
    )
);

CREATE INDEX account_transaction_account_date_ix
    ON account_transaction (account_id, trade_date);

CREATE INDEX account_transaction_instrument_date_ix
    ON account_transaction (instrument_id, trade_date)
    WHERE instrument_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- price: daily close per instrument.
--
-- Composite primary key rather than a surrogate id: you always look this up by
-- (instrument, date) or (instrument, date range), never by an opaque row id.
-- ---------------------------------------------------------------------------
CREATE TABLE price (
    instrument_id  BIGINT         NOT NULL REFERENCES instrument (id),
    price_date     DATE           NOT NULL,
    close          NUMERIC(20,8)  NOT NULL,
    source         VARCHAR(64)    NOT NULL,
    fetched_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    PRIMARY KEY (instrument_id, price_date)
);

-- ---------------------------------------------------------------------------
-- fx_rate: you are in Canada holding USD securities, so almost every valuation
-- needs a rate. Storing them makes historical valuation reproducible instead
-- of dependent on a live lookup.
-- ---------------------------------------------------------------------------
CREATE TABLE fx_rate (
    base_currency   VARCHAR(3)      NOT NULL,
    quote_currency  VARCHAR(3)      NOT NULL,
    rate_date       DATE            NOT NULL,
    rate            NUMERIC(20,10)  NOT NULL,
    source          VARCHAR(64)     NOT NULL,

    PRIMARY KEY (base_currency, quote_currency, rate_date)
);

-- ---------------------------------------------------------------------------
-- corporate_action: splits retroactively change the meaning of historical
-- quantities and prices. Keeping them as separate records means the original
-- transactions stay untouched and adjustment happens at read time.
-- ---------------------------------------------------------------------------
CREATE TABLE corporate_action (
    id             BIGSERIAL      PRIMARY KEY,
    instrument_id  BIGINT         NOT NULL REFERENCES instrument (id),
    action_type    VARCHAR(32)    NOT NULL,
    ex_date        DATE           NOT NULL,
    ratio          NUMERIC(20,8),
    new_symbol     VARCHAR(32),
    note           VARCHAR(1000),

    CONSTRAINT corporate_action_type_ck CHECK (
        action_type IN ('SPLIT','REVERSE_SPLIT','SYMBOL_CHANGE','MERGER','SPINOFF')
    ),
    CONSTRAINT corporate_action_uq UNIQUE (instrument_id, action_type, ex_date)
);
