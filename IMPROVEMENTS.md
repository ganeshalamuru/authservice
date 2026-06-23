# Authservice — Improvement Notes

This document captures concerns and recommendations for evolving `authservice` into an
**authoritative auth server** that other projects can rely on. Findings are grouped into:
OAuth usage, the core token-model problem, security/correctness fixes, code design,
database design, and a recommended target architecture.

File/line references point at the code as it exists today.

---

## 1. Is OAuth actually being used? No — not really.

The project pulls in the **OAuth2 Authorization Server** dependency (`build.gradle:26`) but
never uses it. There are no standard endpoints (`/oauth2/token`, `/oauth2/authorize`, a JWKS
endpoint, or `/.well-known/openid-configuration`). What actually exists is:

- A **custom username/password filter** (`UsernamePasswordJwtTokenAuthenticationFilter`) that
  hands off to `DaoAuthenticationProvider`.
- A hand-rolled JWT minted via `JwtEncoder` in `AuthService.generateToken` (`AuthService.java:60`).
- The Spring **resource server** (`oauth2ResourceServer().jwt()`) validating that JWT's signature.

So the *resource-server* half of OAuth2 is used to validate self-issued custom tokens. That is a
valid pattern, but it is **not OAuth2/OIDC**, and that distinction matters for the authoritative
multi-project goal.

---

## 2. Core problem: a stateful token disguised as a stateless JWT

This breaks the main reason other projects would want a JWT.

Flow today:
1. `generateToken` signs a JWT **and** saves it to Postgres (`app_user_token`) **and** to Redis
   keyed by `userId` (`AuthService.java:77-79`).
2. Every protected call runs the `JwtValid` AOP aspect, which re-reads the token from Redis and
   compares strings (`JwtTokenValidation.java:24-32`).

Consequence: **other projects cannot validate a token on their own.** A consumer would either:
- Trust the signature statelessly (but then the Redis revocation check is meaningless to them — a
  "logged out" token still verifies), or
- Call back into authservice for every request (then it is not really a JWT, it is a session plus
  a network hop per call).

Pick a lane:

| Approach | You get | You give up |
|---|---|---|
| **Stateless JWT + JWKS** | Other services validate offline via your public keys; horizontal scale | Hard to revoke before expiry (mitigate with short TTL + refresh) |
| **Opaque token + introspection endpoint** (RFC 7662) | Central revocation, full control | A network call per validation |

Currently the design takes the costs of both and the benefits of neither.

---

## 3. Security / correctness issues

1. **`X-User-Id` is client-supplied and trusted.** Both logout (`AuthController.java:51`) and the
   aspect (`JwtTokenValidation.java:27`) read the user id from a request header instead of from the
   authenticated JWT. A caller can send any id. Derive identity from the JWT `sub`/principal only.

2. **`sub` is the username, not a stable id** (`AuthService.java:72`). Usernames can change; the
   subject should be the user UUID. Then the `X-User-Id` header is unnecessary.

3. **No `aud` (audience) claim.** With multiple consuming projects this is essential — each service
   should verify the token was minted for it. Also `issuer` is `"self"` (`JWTConstants.java:9`); it
   should be the real issuer URL.

4. **No JWKS endpoint / no `kid` / no key rotation.** `jwsHeader()` is built once with no key id
   (`SecurityConfiguration.java:122`). Consumers currently need `publickey.pem` copied by hand.
   Expose a JWKS endpoint so resource servers fetch keys automatically and keys can be rotated.

5. **No refresh tokens.** Only a 1-hour access token (`AuthService.java:64`), then re-login. An
   authoritative server needs refresh + rotation.

6. **Raw JWTs stored in the DB** (`app_user_token.access_token`). If the DB leaks, those are live
   bearer tokens. Store a SHA-256 hash instead.

7. **Aspect NPE / wrong layer.** `getHeader(AUTHORIZATION).split(" ")[1]` (`JwtTokenValidation.java:26`)
   NPEs on a missing/malformed header. Security checks belong in the filter chain, not AOP `@Before`
   advice on controllers.

8. **`generateToken` is not transactional.** DB save + Redis save are not atomic
   (`AuthService.java:77-79`); a Redis failure leaves an orphaned DB row.

---

## 4. Code design improvements

- **Drop or actually use** `spring-security-oauth2-authorization-server`. Either delete it (unused)
  or adopt it properly — it provides `/oauth2/token`, JWKS, discovery, refresh tokens, and client
  registration out of the box, which is exactly what a multi-project authoritative server needs.
- **Single role per user** (`User.role` ManyToOne, `User.java:30`) is limiting for shared RBAC. Move
  to many-to-many roles → authorities, and consider per-client scopes.
- **Split `AuthService`** into a `TokenService` (mint/persist/revoke) vs `UserService`
  (registration). Token logic, persistence, and Redis are currently tangled.
- **`RedisRepository` is annotated `@Service`** (`RedisRepository.java:7`) — should be
  `@Repository`/`@Component`.
- **Add a global `@ControllerAdvice`** exception handler and real logging; errors like
  `IllegalStateException("user token in missing")` (`AuthService.java:86`) are not mapped to proper
  status codes.
- **`AccessTokenResponse` exposes raw `userId`** and omits the standard OAuth shape (`token_type`,
  `expires_in`, `refresh_token`, `scope`). Match the convention if other services consume it.
- **Real tests** — `AuthserviceApplicationTests` is effectively empty.
- Remove committed cruft: `old-application.properties`.

---

## 5. Database design improvements

1. **Username uniqueness is broken.** The `@UniqueConstraint(columnNames = "username")` is on the
   `User` entity (`User.java:22`), but `username` lives in `app_user_credential`, not `app_user`. The
   migration (`V1__Initial.sql`) has **no unique constraint on username at all**, and `createUser`
   never checks for an existing user (`AuthService.java:52`). Duplicate usernames are possible. Add a
   unique index on `app_user_credential(username)` and pre-check on signup.
2. **Index foreign keys.** Postgres does not auto-index FKs — add indexes on `app_user.role_id`,
   `app_user_credential.user_id`, `app_user_token.user_id`.
3. **Token table:** store a `token_hash` (indexed) instead of the raw `VARCHAR(1000) UNIQUE` JWT, and
   add an explicit `expires_at` column for querying/cleanup. Nothing prunes this table today — it
   grows forever.
4. **Case-insensitive usernames** — use `citext` or store lowercased to prevent `Alice`/`alice`
   duplicates.
5. **One token per user in Redis.** Keyed by `userId` (`AuthService.java:79`), a new login overwrites
   the old — no multi-device sessions. Key by token id (jti) instead, with a TTL matching expiry.
6. **`status_enum` as a Postgres ENUM** is painful to evolve; prefer `VARCHAR + CHECK`.
7. **For true multi-project/OAuth:** add a `registered_client` table (client_id/secret, allowed
   scopes, redirect URIs, allowed audiences) and a `refresh_token` table. The authorization-server
   starter manages these if adopted.

---

## 6. Recommended target architecture

If the goal is "authoritative server my other projects trust":

1. **Adopt the Spring Authorization Server properly** — it provides standard `/oauth2/token`, a
   **JWKS endpoint**, OIDC discovery, refresh tokens, and registered clients out of the box.
2. **Make access tokens short-lived (5–15 min), stateless, with `kid` + JWKS** so consumers validate
   offline. Add `aud` per client.
3. **Move revocation to refresh tokens** (rotate + store hashed in DB). Keep an **introspection
   endpoint** only if a consumer genuinely needs immediate revocation of access tokens.
4. **Identity comes from the JWT only** — delete the `X-User-Id` header pattern entirely.

---

## 7. Suggested priority order

1. ~~**Fix username uniqueness + signup duplicate check** (correctness bug, low risk).~~ ✅ Done — `existsByUsername` pre-check on signup + unique index migration.
2. ~~**Stop trusting `X-User-Id`; derive identity from JWT `sub`** (security, low risk).~~ ✅ Done — identity now read from the JWT, `X-User-Id` header removed.
3. ~~**Use UUID as `sub`; add `aud` and a real `issuer`** (security, medium).~~ ✅ Done — `sub` is the user UUID, configurable `aud`/`issuer` (`jwt.audience`/`jwt.issuer`), and the resource-server decoder now validates `iss` + `aud`.
4. ~~**Decide the token model** (stateless+JWKS vs introspection) — unblocks everything else.~~ ✅ Done — committed to **stateless JWT + JWKS**. Added a `kid` (JWK thumbprint) to the signing key and a public `GET /oauth2/jwks` endpoint so resource servers validate offline and keys can rotate; shortened the access-token TTL to 15 min (`jwt.access-token-ttl`); removed the per-request Redis revocation (`@JwtValid` aspect deleted) so validation is truly stateless. **Accepted tradeoff:** access tokens cannot be revoked before expiry until refresh-token rotation lands in #5 — `logout` now only marks the `app_user_token` row inactive (audit), it does not revoke the live token.
5. ~~**Adopt Spring Authorization Server** (refresh tokens, JWKS, clients) — larger effort.~~ ✅ Done —
   adopted SAS with the standard **Authorization Code + PKCE** grant. Standard endpoints
   (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/oauth2/revoke`, OIDC discovery); users
   authenticate at a form-login page backed by the existing `CustomDaoAuthenticationProvider`.
   **Refresh tokens with rotation** (`reuseRefreshTokens(false)`) enable revocation before
   access-token expiry via `/oauth2/revoke`. Authorizations + registered clients are **JDBC-persisted**
   in Postgres (Flyway `V3__sas_schema.sql`, Postgres-adapted SAS schema); the client
   (`authservice-client`, PKCE-required) is seeded by `RegisteredClientInitializer`. A
   `JwtEncodingContext` customizer preserves the #1–#4 token contract (`sub`=UUID, `role`, `aud`,
   `iss`). The custom login filter / `AuthService.generateToken` were removed; `app_user_token` is now
   unused (table cleanup deferred to #6).
6. ~~**DB hardening** (FK indexes, hashed tokens, token cleanup).~~ ✅ Done — the legacy
   `app_user_token` table (the source of the "raw tokens in the DB" + "grows forever" concerns) is
   **dropped** in Flyway `V4`, since SAS now persists tokens in `oauth2_authorization`; that also
   removes the `UserToken` entity/repository and the stale `@UniqueConstraint(columnNames="username")`
   on `app_user`. `V4` also adds the missing FK indexes (`ix_app_user_role_id`,
   `ix_app_user_credential_user_id`). **Deferred:** scheduled pruning of expired
   `oauth2_authorization` rows (SAS state) — tracked for a later task.
7. ~~**Code hygiene** (split services, global error handler, tests, remove cruft).~~ ✅ Done —
   most sub-items were absorbed by the SAS migration (global `@RestControllerAdvice` already exists;
   `RedisRepository`/`AccessTokenResponse`/token-minting logic gone, so nothing left to split out of
   `AuthService`). This pass: **removed Redis entirely** (dependency, `RedisConfig`, the
   `spring.data.redis` config, the compose `redis` service — it was never in the auth path); deleted
   the committed `old-application.properties` cruft; and added a **real test suite** — Mockito unit
   tests (`AuthServiceTest`, `UserServiceTest`), a `@WebMvcTest` controller slice (`AuthControllerTest`),
   and a Testcontainers `@SpringBootTest` that boots the full app against a real Postgres (Flyway
   V1–V4, JWKS/discovery/signup/resource-server assertions).

---

## Phase 2 — code design, architecture, and Spring Boot 4 / Java 25 modernization

Phase 1 (items 1–7 above) is complete. This phase captures the next round, found in a full re-read of
the codebase. Items are grouped and tagged with rough priority; `✅ Done — Pass N` markers are filled
in as work lands.

### A. Correctness / latent bugs (do first)

1. **`@ConfigurationProperties` validation was never enforced.** `JwtProperties`, `DatabaseProperties`,
   and `RegisteredClientProperties` carry `@NotBlank`/`@NotNull` but lacked `@Validated`, so the
   constraints never ran — a blank `JWT_AUDIENCE=` or empty client secret would not fail fast at
   startup. ✅ Done — Pass 1: added `@Validated` to all three.
2. **`ADMIN` was unreachable + `SUPER_ADMIN_PASSWORD` documented but unused.** `RoleName.ADMIN` is
   referenced by the security rules (`SecurityConfiguration`, `UserController`), but no ADMIN role was
   seeded, nothing read `SUPER_ADMIN_PASSWORD`, and no admin user was ever created — the ADMIN branch
   was dead. ✅ Done — Pass 1: `RoleInitializer` now seeds every `RoleName`; a new
   `SuperAdminInitializer` (idempotent, `@Order(2)`) seeds an ADMIN super-user from the `super-admin.*`
   properties (`SUPER_ADMIN_PASSWORD`, optional `SUPER_ADMIN_USERNAME`/`_FIRST_NAME`/`_LAST_NAME`),
   skipping with a warning when the password is unset.
3. **Hand-built `DataSource` bypassed Boot's Hikari binding.** `FlyWayConfiguration` constructed the
   `DataSource` via `DataSourceBuilder`, which made Boot's autoconfiguration back off — so
   `spring.datasource.hikari.*` (e.g. `tcpKeepAlive`) was silently ignored. ✅ Done — Pass 2: deleted
   `FlyWayConfiguration`, `SecretProvider`, and `DatabaseProperties`; Boot now autoconfigures the
   datasource (Hikari props bind) and Flyway (`spring.flyway.baseline-on-migrate: true`). Secrets load
   via config tree (see #11).
4. **Soft-delete is modeled but never enforced.** `BaseEntity.deletedAt`/`status` are set ACTIVE on
   create and never used afterward; `findAll()` returns "deleted" rows. Decide: wire it up
   (`@SQLRestriction("deleted_at is null")` + a real delete path) or drop the columns. **Pending.**

### B. Code design

5. `UserResponse` serializes the full `Role` JPA entity (drags `BaseEntity` audit fields into the API,
   lazy-load risk) — expose a `roleName` string. **Pending.**
6. `UserCredential.user` is `@ManyToOne(cascade = ALL)` but is conceptually one-to-one — model as
   `@OneToOne`. **Pending.**
7. Repository finders return `null` (`findByUsername`, `findByName`) — return `Optional<>` and
   `orElseThrow` the missing-`USER`-role case in `AuthService`. **Pending.**
8. `AuthService` no longer does auth (only registration, post-SAS) — rename to `RegistrationService`.
   **Pending.**
9. Hygiene — ✅ Done — Pass 1: explicit imports in `UserController` (dropped the wildcard),
   `@Transactional(readOnly = true)` on `getAllUsers`, removed the redundant `lombok.Getter` import in
   `UserSignupRequest`, and `server.port` is now `${PORT:8081}`. Still pending: `status_enum` Postgres
   ENUM → `VARCHAR + CHECK` (the old #5.6 nit).

### C. Spring Boot 4 / Spring Framework 7 features to adopt

10. RFC 7807 `ProblemDetail` for error bodies + a handler for `MethodArgumentNotValidException`
    (validation 400s currently fall through to the default `/error`). **Pending.**
11. ✅ Done — Pass 2: secrets load via Spring Boot config tree
    (`spring.config.import=configtree:${SECRETS_DIR:/run/secrets}/`); the `db_password` and
    `jwt_jwks` files map to `spring.datasource.password` / `jwt.jwk-set`. `SecretProvider` +
    the SpEL injection are gone; docker-compose secret mounts unchanged; local runs set `SECRETS_DIR`.
12. Native API versioning (`ApiVersionConfigurer`) instead of the hard-coded `/api/v1/` path. **Pending.**
13. Virtual threads — `spring.threads.virtual.enabled=true` (blocking JDBC + bcrypt on Java 25). **Pending.**
14. JSpecify null-safety to replace the `Objects.isNull/nonNull` chains in the token customizer. **Pending.**

### D. Java 25 / modeling

15. Records for immutable DTOs (`UserResponse`, `UserSignupRequest`) and the constructor-bound
    `@ConfigurationProperties` classes (`@DefaultValue`). **Pending.**
16. Spring Data JPA auditing (`@CreatedDate`/`@LastModifiedDate` + `@EnableJpaAuditing`) replacing the
    hand-rolled `@PrePersist`/`@PreUpdate`; switch audit timestamps to `Instant`/`timestamptz` (UTC). **Pending.**

### E. Architecture / security (forward-looking)

17. Scheduled pruning of expired `oauth2_authorization` rows (the item explicitly deferred from
    Phase 1 #6 — SAS state otherwise grows forever).
18. Observability/ops: add `spring-boot-starter-actuator` (health/readiness probes for compose/k8s),
    `server.forward-headers-strategy` (correct issuer URL behind a proxy), and rate-limiting on `/login`
    and `/auth/signup`.
19. Multi-project RBAC: many-to-many user↔roles↔authorities and per-client audience/scopes (resource
    indicators) instead of the single `@ManyToOne` role + single global `aud`.
20. Test the token customizer end-to-end (mint a real SAS token; assert `sub`=UUID / `role` / `aud`) —
    currently the most security-critical custom code is uncovered.
