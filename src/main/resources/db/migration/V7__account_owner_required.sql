-- ===========================================================================
-- V7: every account must have an owner
--
-- user_id was nullable in V6 only because accounts existed before users did,
-- and the bootstrap claimed the orphans on first startup. Leaving it nullable
-- permanently would mean a bug that forgets to set the owner produces a row
-- no user can see and no query filters out, which is exactly the kind of
-- silent hole that ownership checks are supposed to close.
--
-- The guard below fails the migration rather than the constraint, so the
-- error says what is wrong instead of just naming a violated constraint.
-- ===========================================================================

DO $$
DECLARE
    orphans BIGINT;
BEGIN
    SELECT count(*) INTO orphans FROM account WHERE user_id IS NULL;
    IF orphans > 0 THEN
        RAISE EXCEPTION
            'Cannot make account.user_id NOT NULL: % account(s) have no owner. '
            'Start the app once with APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD '
            'set so the bootstrap can claim them.', orphans;
    END IF;
END $$;

ALTER TABLE account
    ALTER COLUMN user_id SET NOT NULL;
