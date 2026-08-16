-- ===========================================================================
-- V2: symbol resolution
--
-- The Questrade export refers to the same holding by up to three different
-- strings:
--   * the traded ticker            T.TO, VUG, NVDA, DLR.TO
--   * a leading-dot variant        .T, .BCE, .ENB      (dividend rows)
--   * an opaque internal code      N003056, V003096    (dividend/split rows)
--
-- Without resolution, each string becomes its own instrument and every
-- position and cost-basis figure computed from them is wrong.
--
-- company_key is derived from the row Description, which names the issuer in
-- every scheme. It is the join that lets an opaque code find its ticker.
-- instrument_alias records each raw string once it has been resolved, so the
-- mapping is inspectable rather than buried in code.
-- ===========================================================================

ALTER TABLE instrument
    ADD COLUMN company_key VARCHAR(255);

-- Partial unique index: rows whose description was missing have a NULL
-- company_key and are simply not covered by the constraint.
CREATE UNIQUE INDEX instrument_company_key_uq
    ON instrument (company_key)
    WHERE company_key IS NOT NULL;

CREATE TABLE instrument_alias (
    raw_symbol     VARCHAR(64)  PRIMARY KEY,
    instrument_id  BIGINT       NOT NULL REFERENCES instrument (id),
    source         VARCHAR(32)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT instrument_alias_source_ck CHECK (
        source IN ('EXACT', 'NORMALISED', 'COMPANY_KEY', 'MANUAL', 'UNRESOLVED')
    )
);

CREATE INDEX instrument_alias_instrument_ix ON instrument_alias (instrument_id);
