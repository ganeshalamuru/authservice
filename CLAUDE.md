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
- `SPRING_DATASOURCE_PASSWORD_FILE` (path to the DB password file, e.g. `~/Documents/db_password.txt`)
- `JWT_PRIVATE_KEY_FILE` (path to the base64 private key, e.g. `~/Documents/b64private.txt`)
- `SUPER_ADMIN_PASSWORD` (e.g. `superadmin@123`)
- optional: `OAUTH2_CLIENT_SECRET` — secret for the seeded Authorization Server client
  (`authservice-client`). Defaults to `dev-client-secret` for local runs; set a real value outside
  dev. Related optional overrides: `OAUTH2_CLIENT_ID`, `OAUTH2_REDIRECT_URIS` (comma-separated),
  `OAUTH2_REFRESH_TOKEN_TTL`.
- optional: `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` (Redis is no longer in the auth
  path, so the app starts without it)

## Stack

- **Java 25** toolchain, **Spring Boot 4.1**, Gradle (`build.gradle`, Groovy DSL).
- **PostgreSQL** (JPA) + **Flyway** migrations in `src/main/resources/db/migration`.
- **Spring Authorization Server** (`spring-security-oauth2-authorization-server`) for OAuth2/OIDC.
- **Redis** dependency is still on the classpath but no longer used in the auth path.
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
  resource-server for `/api/v1/**`, (3) default form-login chain (`/login`, `/auth/signup`, docs).
- **Stateless** access tokens validated by the resource server on signature + `iss` + `aud` only.
  A `JwtEncodingContext` customizer preserves: `sub` = user **UUID**, roles in the `role` claim,
  `aud` = `jwt.audience`, `iss` = `jwt.issuer` (= `AuthorizationServerSettings.issuer`). Access-token
  TTL is `jwt.access-token-ttl` (default 15 min); tokens are RS256, signed with the existing `kid`ed key.
- **Refresh tokens with rotation** (`reuseRefreshTokens(false)`); refresh tokens can be revoked via
  `/oauth2/revoke`. SAS state (`oauth2_authorization`, `oauth2_registered_client`,
  `oauth2_authorization_consent`) is persisted in Postgres via the JDBC services (Flyway `V3`).
- The OAuth2 client (`authservice-client`) is seeded on startup by `RegisteredClientInitializer`
  (PKCE-required, secret from `OAUTH2_CLIENT_SECRET`). `/auth/signup` is the only remaining custom
  auth endpoint (user registration); the legacy `app_user_token` table is now unused (cleanup in #6).
- Redis is not in the auth path. See `IMPROVEMENTS.md` for the roadmap (items 1–5 done).

## Conventions

- Config via `@ConfigurationProperties` classes registered in `AuthserviceApplication` (e.g. `DatabaseProperties`, `JwtProperties`).
- Secrets read from files at runtime (`SecretProvider`) — DB password and JWT private key; never hard-code them.
- `application.yml` pulls deployment values from env vars with sensible local defaults.
- Errors surfaced via `ResponseStatusException` / `GlobalExceptionHandler`.

## Local infra

Postgres is exposed on host port **5432**
