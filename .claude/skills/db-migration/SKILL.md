---
name: db-migration
description: Scaffold a new Flyway database migration for the authservice repo following its conventions. Use whenever a change needs a schema change, new table/column/index, or data backfill — anything under src/main/resources/db/migration.
---

# Add a Flyway migration

Flyway migrations live in `src/main/resources/db/migration` and are applied at app
startup. They are **versioned and forward-only**: never edit a migration that has already
been applied (it breaks Flyway's checksum) — always add a new `V{n}` file.

## Steps

1. **Find the next version.** List the existing files (currently up to `V6`). The next file
   is `V{n+1}`. Name it `V{n}__{snake_case_description}.sql` — double underscore after the
   version, lowercase snake_case description. Examples in-repo:
   - `V5__soft_delete_partial_username_index.sql`
   - `V6__audit_timestamptz_and_status_varchar.sql`

2. **Write a header comment** explaining *why*, not just what. Reference the relevant
   `IMPROVEMENTS.md` phase/item if the change maps to one (the existing migrations do, e.g.
   `-- Phase 2 #4: ...`). State any data-interpretation tradeoffs explicitly.

3. **Follow the established Postgres patterns** (dialect is `flyway-database-postgresql`):
   - **Soft-delete uniqueness** → partial unique index, not a plain one:
     `CREATE UNIQUE INDEX ... ON tbl (col) WHERE deleted_at IS NULL;`
     (the entities use `@SQLRestriction "deleted_at is null"`).
   - **Status / small enumerations** → `varchar(20) + CHECK (col IN (...))`, **not** a
     Postgres `ENUM` type (enums are painful to evolve — V6 migrated away from them).
   - **Audit timestamps** → `timestamptz` (UTC). JPA auditing stamps `Instant`.
   - Guard with `IF EXISTS` / `IF NOT EXISTS` so re-runs against partially-migrated dev DBs
     don't hard-fail.

4. **Know which tables are yours vs. SAS-managed.** App tables: `app_role`, `app_user`,
   `app_user_credential`. The `oauth2_authorization`, `oauth2_registered_client`,
   `oauth2_authorization_consent` tables are owned by Spring Authorization Server (created in
   `V3`) — **do not alter them by hand**; they manage their own columns/timestamps.

5. **Validate.** Build via the JetBrains MCP (`mcp__jetbrains__build_project`). To actually
   apply + test the migration you need **Docker running** — tests use Testcontainers
   (`testcontainers-postgresql`), and a local run hits Postgres on host port `5432`. See the
   `build-check` skill for the run/validate loop.

## Don'ts
- Don't edit or delete an already-applied `V{n}` file — add a new one.
- Don't reuse a version number or skip numbers.
- Don't hand-edit `oauth2_*` tables.
- Don't reintroduce Postgres `ENUM` types.
