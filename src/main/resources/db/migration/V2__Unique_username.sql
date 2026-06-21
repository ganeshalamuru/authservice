-- Enforce username uniqueness at the database level (complements the application-level
-- pre-check in AuthService.createUser, closing the concurrent-signup race).
CREATE UNIQUE INDEX IF NOT EXISTS "ux_app_user_credential_username"
  ON "app_user_credential" ("username");
