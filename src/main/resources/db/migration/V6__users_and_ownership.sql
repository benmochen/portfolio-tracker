-- ===========================================================================
-- V6: users, and accounts belonging to them
--
-- Authentication alone is not access control. Requiring a login while every
-- endpoint still serves any account by id means any logged-in user can read
-- anyone's holdings by changing a number in the URL. Ownership is the part
-- that actually protects the data.
-- ===========================================================================

CREATE TABLE app_user (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(64)  NOT NULL,
    -- BCrypt hash, always 60 characters. The plaintext password is never
    -- stored, never logged, and cannot be recovered from this column.
    password_hash  VARCHAR(72)  NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT app_user_username_uq UNIQUE (username)
);

-- Nullable on purpose, for one reason only: accounts already exist in this
-- database, created by the importer before users did. The bootstrap claims
-- them for the first user on startup. A system designed with users from the
-- start would make this NOT NULL, and it should become NOT NULL once the
-- existing rows are claimed.
ALTER TABLE account
    ADD COLUMN user_id BIGINT REFERENCES app_user (id);

CREATE INDEX account_user_ix ON account (user_id);
