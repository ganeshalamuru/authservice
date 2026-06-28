-- Phase 2 D16 + B9: modeling cleanup of the BaseEntity columns.
--
-- D16: move the audit timestamps off naive TIMESTAMP (no zone) to timestamptz. Existing values were
--      written with LocalDateTime.now() (system zone); we interpret them AS UTC here -- an accepted
--      tradeoff for existing dev data. Going forward Spring Data auditing stamps Instant (UTC).
-- B9:  the status_enum Postgres ENUM is painful to evolve -> switch to VARCHAR + CHECK.
--
-- Scope: app_role, app_user, app_user_credential -- the only tables with these columns (status_enum
-- and the audit columns are defined only in V1; the app_user_token table that also used the enum was
-- dropped in V4). The SAS oauth2_* tables manage their own timestamps and are untouched.

-- app_role ---------------------------------------------------------------------------------------
ALTER TABLE app_role ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC';
ALTER TABLE app_role ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE app_role ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE app_role ALTER COLUMN status DROP DEFAULT;
ALTER TABLE app_role ALTER COLUMN status TYPE varchar(20) USING status::text;
ALTER TABLE app_role ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE app_role ADD CONSTRAINT app_role_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- app_user ---------------------------------------------------------------------------------------
ALTER TABLE app_user ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC';
ALTER TABLE app_user ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE app_user ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE app_user ALTER COLUMN status DROP DEFAULT;
ALTER TABLE app_user ALTER COLUMN status TYPE varchar(20) USING status::text;
ALTER TABLE app_user ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE app_user ADD CONSTRAINT app_user_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- app_user_credential ----------------------------------------------------------------------------
ALTER TABLE app_user_credential ALTER COLUMN created_at TYPE timestamptz USING created_at AT TIME ZONE 'UTC';
ALTER TABLE app_user_credential ALTER COLUMN updated_at TYPE timestamptz USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE app_user_credential ALTER COLUMN deleted_at TYPE timestamptz USING deleted_at AT TIME ZONE 'UTC';
ALTER TABLE app_user_credential ALTER COLUMN status DROP DEFAULT;
ALTER TABLE app_user_credential ALTER COLUMN status TYPE varchar(20) USING status::text;
ALTER TABLE app_user_credential ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE app_user_credential ADD CONSTRAINT app_user_credential_status_check CHECK (status IN ('ACTIVE', 'INACTIVE'));

-- The enum type is now unused.
DROP TYPE status_enum;
