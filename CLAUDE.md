# CLAUDE.md

Guidance for working in the `authservice` repository.

## Build / run — use the IntelliJ MCP

Build, compile, and run this project through the **JetBrains (IntelliJ) MCP**, not `./gradlew` or shell commands:

- Validate edits with `mcp__jetbrains__build_project` (pass `projectPath`).
- Surface inspections/errors with `mcp__jetbrains__get_file_problems`.
- Launch via `mcp__jetbrains__execute_run_configuration` / run configurations.

Only fall back to `./gradlew` if the user explicitly asks.

### ⚠️ Before suggesting `rm` of anything that affects how the app runs — back it up first

Deleting files/folders that hold run-time configuration can silently break startup. This already
happened once: deleting the **`.idea/`** folder (advised for a visual bug) wiped the IntelliJ run
configuration that carried the app's environment variables, so the app could no longer start.

Before recommending deletion of `.idea/`, run configurations, `~/app-secrets/`, env files, or any
config that changes how the app runs or behaves: **call it out and back up the necessary values
first.** The run configuration for `AuthserviceApplication` needs these env vars (point them at
local Postgres / secret files):

- `SPRING_DATASOURCE_URL` (e.g. `jdbc:postgresql://localhost:5432/authservice`)
- `SPRING_DATASOURCE_USERNAME` (local run uses `postgres`; the docker-compose stack uses `admin`)
- `SECRETS_DIR` (directory of secret files loaded via `spring.config.import=configtree:`). It must hold
  two **extensionless** files: `db_password` (the DB password) and `jwt_jwks` (a JWK Set JSON holding
  the RS256 signing key — private params included; the first key in the set signs). E.g.
  `SECRETS_DIR=C:/Users/ganes/Documents/secrets`. Defaults to `/run/secrets/` (where docker-compose
  mounts the secrets) when unset.
- `SUPER_ADMIN_PASSWORD` (e.g. `superadmin@123`)
- optional: `OAUTH2_CLIENT_SECRET` — secret for the seeded Authorization Server client
  (`authservice-client`). Defaults to `dev-client-secret` for local runs; set a real value outside
  dev. Related optional overrides: `OAUTH2_CLIENT_ID`, `OAUTH2_REDIRECT_URIS` (comma-separated),
  `OAUTH2_REFRESH_TOKEN_TTL`.

## Stack

- **Java 25** toolchain, **Spring Boot 4.1**, Gradle (`build.gradle`, Groovy DSL).
- **PostgreSQL** (JPA) + **Flyway** migrations in `src/main/resources/db/migration`.
- **Spring Authorization Server** (`spring-security-oauth2-authorization-server`) for OAuth2/OIDC.
- **Lombok** (`@Data`, `@RequiredArgsConstructor`, etc.) — constructor injection via `@RequiredArgsConstructor` on `final` fields.
- **springdoc-openapi** Swagger UI.
- Base package: `com.gan.authservice`. Runs on port **8081**.

## Auth model (current state)

- **Spring Authorization Server (SAS)** drives auth: standard `/oauth2/authorize` + `/oauth2/token`
  (Authorization Code + PKCE), `/oauth2/jwks`, `/oauth2/revoke`, and OIDC discovery
  (`/.well-known/openid-configuration`). Config in `AuthorizationServerConfiguration`.
- Users authenticate at the form-login page (`/login`) via `CustomDaoAuthenticationProvider` +
  `CustomUserDetailsService`; SAS then issues the code → tokens. There is no custom login filter.
- **Three security filter chains** (`@Order`): (1) SAS endpoints + OIDC, (2) stateless
  resource-server for `/api/**`, (3) default form-login chain (`/login`, `/auth/signup`, docs).
- **API versioning** is native (Spring MVC, `WebMvcConfiguration`): the version travels in the
  `X-API-Version` header (optional, defaults to `1`), so endpoints are `/api/users` (no `/v1/`
  segment) and controllers declare `@RequestMapping(version = "1")`.
- **Stateless** access tokens validated by the resource server on signature + `iss` + `aud` only.
  A `JwtEncodingContext` customizer preserves: `sub` = user **UUID**, roles in the `role` claim,
  `aud` = `jwt.audience`, `iss` = `jwt.issuer` (= `AuthorizationServerSettings.issuer`). Access-token
  TTL is `jwt.access-token-ttl` (default 15 min); tokens are RS256, signed with the existing `kid`ed key.
- **Refresh tokens with rotation** (`reuseRefreshTokens(false)`); refresh tokens can be revoked via
  `/oauth2/revoke`. SAS state (`oauth2_authorization`, `oauth2_registered_client`,
  `oauth2_authorization_consent`) is persisted in Postgres via the JDBC services (Flyway `V3`).
- The OAuth2 client (`authservice-client`) is seeded on startup by `RegisteredClientInitializer`
  (PKCE-required, secret from `OAUTH2_CLIENT_SECRET`). `/auth/signup` is the only remaining custom
  auth endpoint (user registration); the legacy `app_user_token` table was dropped in `V4`.
- Redis has been removed entirely (it was never in the auth path). See `IMPROVEMENTS.md` for the
  roadmap (items 1–7 done; SAS `oauth2_authorization` pruning intentionally deferred).

## Conventions

- Config via `@ConfigurationProperties` classes registered in `AuthserviceApplication` (e.g. `JwtProperties`, `RegisteredClientProperties`).
- Secrets loaded from files via Spring Boot **config tree** (`spring.config.import=configtree:` → `SECRETS_DIR`) — DB password and JWT private key; never hard-code them.
- Regenerate the `jwt_jwks` secret with `node scripts/gen-jwks.js` (no deps; `--compact` for a
  one-line file, `--from-pem` to preserve an existing key). The **first** key in the set signs;
  rotate by prepending a fresh key. Write it to the extensionless `$SECRETS_DIR/jwt_jwks` file.
- `application.yml` pulls deployment values from env vars with sensible local defaults.
- Errors surfaced via `ResponseStatusException` / `GlobalExceptionHandler`.
- **Flyway migrations** (`src/main/resources/db/migration`) are versioned + **forward-only** —
  never edit an applied `V{n}` file; add a new `V{n}__snake_desc.sql` (next is `V7`). Postgres
  dialect; established patterns: **partial** unique index (`WHERE deleted_at IS NULL`) for
  soft-delete uniqueness, `varchar(20) + CHECK` over Postgres `ENUM`, `timestamptz` audit columns.
  The `oauth2_*` tables are SAS-owned (created in `V3`) — don't hand-edit them. See the
  `db-migration` skill.

## Testing

- Tests use **Testcontainers** (`testcontainers-postgresql`), so **Docker must be running** or
  they fail at startup.
- **CI does not run the tests** — the GitHub Actions build is `./gradlew clean build -x test`.
  Tests are effectively local-only; run them yourself before pushing if you touched tested code.

## Project automation (`.claude/`)

- Skills: `build-check` (build/inspect/run via the JetBrains MCP), `db-migration` (scaffold a
  Flyway migration to convention).
- Agent: `auth-security-reviewer` — OAuth2/SAS-tuned security review; prefer it over the generic
  review for changes to security config, the token customizer, SAS setup, or the resource server.

## Local infra

Postgres is exposed on host port **5432**. Docker is available but check if it's up or not —
it's required for running the Testcontainers-based tests as well as the DB.
