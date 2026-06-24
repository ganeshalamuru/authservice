-- Phase 2 #4: soft-delete is now enforced (@SQLRestriction "deleted_at is null" on the user
-- entities). The original full unique index on username (V2) would keep a soft-deleted user's
-- name reserved forever. Replace it with a PARTIAL unique index that only constrains live rows,
-- so a freed username can be reused after a soft-delete while still blocking duplicate active users.
DROP INDEX IF EXISTS "ux_app_user_credential_username";

CREATE UNIQUE INDEX IF NOT EXISTS "ux_app_user_credential_username_active"
  ON "app_user_credential" ("username")
  WHERE deleted_at IS NULL;
