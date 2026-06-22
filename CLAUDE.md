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
- optional: `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` (Redis is no longer in the auth
  path, so the app starts without it)

## Stack

- **Java 25** toolchain, **Spring Boot 4.1**, Gradle (`build.gradle`, Groovy DSL).
- **PostgreSQL** (JPA) + **Flyway** migrations in `src/main/resources/db/migration`.
- **Redis** for active-token tracking.
- **Lombok** (`@Data`, `@RequiredArgsConstructor`, etc.) — constructor injection via `@RequiredArgsConstructor` on `final` fields.
- **springdoc-openapi** Swagger UI.
- Base package: `com.gan.authservice`. Runs on port **8081**.

## Auth model (current state)

- Custom username/password filter → `DaoAuthenticationProvider`; JWT minted with `JwtEncoder` (RS256) in `AuthService.generateToken`.
- **Stateless** token model: the self-issued JWT is validated by the Spring **resource server** on signature + `iss` + `aud` only — no per-request datastore lookup.
- JWT `sub` = user **UUID**; roles in the `role` claim; configurable `iss`/`aud` via `jwt.issuer` / `jwt.audience`. Access-token TTL is `jwt.access-token-ttl` (default 15 min).
- Signing key carries a `kid` (JWK thumbprint); public keys are served at `GET /oauth2/jwks` so consumers validate offline and keys can rotate.
- Issued tokens are still recorded in Postgres (`app_user_token`) as an audit log; `logout` marks the row inactive but does **not** revoke the live token (no revocation before expiry until refresh tokens land — see #5). Redis is no longer in the auth path.
- See `IMPROVEMENTS.md` for the roadmap and priority order (items 1–4 done).

## Conventions

- Config via `@ConfigurationProperties` classes registered in `AuthserviceApplication` (e.g. `DatabaseProperties`, `JwtProperties`).
- Secrets read from files at runtime (`SecretProvider`) — DB password and JWT private key; never hard-code them.
- `application.yml` pulls deployment values from env vars with sensible local defaults.
- Errors surfaced via `ResponseStatusException` / `GlobalExceptionHandler`.

## Local infra

Postgres is exposed on host port **5432**
