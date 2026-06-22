# CLAUDE.md

Guidance for working in the `authservice` repository.

## Build / run — use the IntelliJ MCP

Build, compile, and run this project through the **JetBrains (IntelliJ) MCP**, not `./gradlew` or shell commands:

- Validate edits with `mcp__jetbrains__build_project` (pass `projectPath`).
- Surface inspections/errors with `mcp__jetbrains__get_file_problems`.
- Launch via `mcp__jetbrains__execute_run_configuration` / run configurations.

Only fall back to `./gradlew` if the user explicitly asks.

## Stack

- **Java 25** toolchain, **Spring Boot 4.1**, Gradle (`build.gradle`, Groovy DSL).
- **PostgreSQL** (JPA) + **Flyway** migrations in `src/main/resources/db/migration`.
- **Redis** for active-token tracking.
- **Lombok** (`@Data`, `@RequiredArgsConstructor`, etc.) — constructor injection via `@RequiredArgsConstructor` on `final` fields.
- **springdoc-openapi** Swagger UI.
- Base package: `com.gan.authservice`. Runs on port **8081**.

## Auth model (current state)

- Custom username/password filter → `DaoAuthenticationProvider`; JWT minted with `JwtEncoder` (RS256) in `AuthService.generateToken`.
- Spring **resource server** validates the self-issued JWT (signature + `iss` + `aud`).
- JWT `sub` = user **UUID**; roles in the `role` claim; configurable `iss`/`aud` via `jwt.issuer` / `jwt.audience`.
- Tokens also stored in Postgres (`app_user_token`) and Redis (keyed by userId); a `@JwtValid` AOP aspect re-checks Redis for revocation.
- See `IMPROVEMENTS.md` for the roadmap and priority order (items 1–3 done).

## Conventions

- Config via `@ConfigurationProperties` classes registered in `AuthserviceApplication` (e.g. `DatabaseProperties`, `JwtProperties`).
- Secrets read from files at runtime (`SecretProvider`) — DB password and JWT private key; never hard-code them.
- `application.yml` pulls deployment values from env vars with sensible local defaults.
- Errors surfaced via `ResponseStatusException` / `GlobalExceptionHandler`.

## Local infra

`docker-compose.yml` provides postgres + redis + the app. Secrets come from `~/app-secrets/` files. Postgres is exposed on host port **5434**, Redis on **6379**.
