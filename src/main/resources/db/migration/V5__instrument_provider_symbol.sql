-- ===========================================================================
-- V5: the ticker your broker uses is not the ticker a data provider uses
--
-- Questrade says "SHOP.TO". Alpha Vantage uses its own suffix for Toronto
-- listings. Rather than encode that translation in Java, it lives here as
-- data, so a wrong symbol is fixed with an UPDATE instead of a deploy.
--
-- The seeded guess below is exactly that: a guess, based on currency. Verify
-- each one against the provider's symbol search before trusting the prices.
-- ===========================================================================

ALTER TABLE instrument
    ADD COLUMN provider_symbol VARCHAR(32);

-- US listings almost always match the plain ticker.
UPDATE instrument
   SET provider_symbol = symbol
 WHERE currency = 'USD';

-- Toronto listings need an exchange suffix. .TRT is Alpha Vantage's, and is
-- the single most likely thing in this migration to be wrong.
UPDATE instrument
   SET provider_symbol = symbol || '.TRT'
 WHERE currency = 'CAD';

-- Tracks what has already been fetched so the job never refetches history it
-- has. With 25 requests a day, a blind refetch would exhaust the quota on the
-- first instrument.
ALTER TABLE instrument
    ADD COLUMN prices_fetched_through DATE;
