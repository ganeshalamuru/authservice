-- #6 DB hardening: the legacy app_user_token table is dead since SAS adoption (tokens now live in
-- oauth2_authorization). Drop it, and add the FK indexes Postgres does not create automatically.
DROP TABLE IF EXISTS "app_user_token";

CREATE INDEX IF NOT EXISTS "ix_app_user_role_id"
  ON "app_user" ("role_id");
CREATE INDEX IF NOT EXISTS "ix_app_user_credential_user_id"
  ON "app_user_credential" ("user_id");
