---
name: build-check
description: Build, inspect, and run the authservice app the way this repo expects — via the JetBrains (IntelliJ) MCP, not ./gradlew. Use to validate edits, surface compile/inspection errors, or launch the app locally.
---

# Build / inspect / run via the JetBrains MCP

This project is built and run through the **IntelliJ MCP**, not `./gradlew`. Reach for
gradlew only if the user explicitly asks.

## Validate edits (the normal loop)

1. **Build:** `mcp__jetbrains__build_project` (pass `projectPath`). This compiles the whole
   project and reports errors.
2. **Inspect changed files:** `mcp__jetbrains__get_file_problems` on each file you edited to
   surface inspections/warnings the build alone won't show.

Fix and repeat until both are clean.

## Run the app locally

Launch `AuthserviceApplication` via `mcp__jetbrains__execute_run_configuration`. The run
configuration carries env vars pointing at local Postgres + secret files. If it's missing
(e.g. `.idea/` was wiped), it needs:

- `SPRING_DATASOURCE_URL` — e.g. `jdbc:postgresql://localhost:5432/authservice`
- `SPRING_DATASOURCE_USERNAME` — `postgres` for local runs (docker-compose uses `admin`)
- `SECRETS_DIR` — dir holding extensionless files `db_password` and `jwt_jwks`
  (e.g. `C:/Users/ganes/Documents/secrets`); defaults to `/run/secrets/` when unset
- `SUPER_ADMIN_PASSWORD` — e.g. `superadmin@123`
- optional: `OAUTH2_CLIENT_SECRET` (defaults to `dev-client-secret` for dev)

App runs on port **8081**. Postgres must be up on host port `5432` (check Docker first).

## Tests — read this before running them

- Tests use **Testcontainers** (`testcontainers-postgresql`), so **Docker must be running**
  or they fail at startup.
- **CI does not run the tests** — the GitHub Actions build is `./gradlew clean build -x test`.
  So tests are effectively local-only; run them yourself before pushing if you touched
  tested code.

## ⚠️ Before deleting any run-time config — back it up first
Deleting `.idea/`, run configurations, `SECRETS_DIR`, or env files can silently break
startup (this already happened once — deleting `.idea/` wiped the run config's env vars).
Call it out and back up the values above first.
