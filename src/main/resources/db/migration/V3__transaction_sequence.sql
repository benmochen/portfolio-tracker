-- ===========================================================================
-- V3: explicit chronological ordering within a trade date
--
-- Cost basis is path-dependent: buying then selling on the same day is not
-- the same as selling then buying. The ledger therefore needs a total order,
-- and the database id cannot supply it.
--
-- Questrade exports newest activity first, so rows are inserted in reverse
-- chronological order and id DESCENDS through time. Ordering by (trade_date,
-- id) is correct across dates and backwards within one, which silently
-- released units at a zero cost basis and reported the full proceeds of a
-- same-day sale as profit.
--
-- sequence_no is assigned at import time so that ordering by
-- (trade_date, sequence_no) is chronological regardless of which direction
-- the source file happened to run in.
-- ===========================================================================

ALTER TABLE account_transaction
    ADD COLUMN sequence_no INTEGER NOT NULL DEFAULT 0;

-- Replaces the old index: every ledger read now sorts by this pair.
DROP INDEX IF EXISTS account_transaction_account_date_ix;

CREATE INDEX account_transaction_account_order_ix
    ON account_transaction (account_id, trade_date, sequence_no);
