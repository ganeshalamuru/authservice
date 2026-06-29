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
   create and never used afterward; `findAll()` returns "deleted" rows. ✅ Done — Pass 4: **wired it
   up.** `@SQLRestriction("deleted_at is null")` on `User` + `UserCredential` hides soft-deleted rows
   from every query (listings *and* the credential/login lookup, so a deleted user can no longer
   authenticate); a `BaseEntity.softDelete()` helper stamps `deletedAt` + flips `status` to INACTIVE.
   Real delete path: `UserService.deleteUser(UUID)` (404 if absent) soft-deletes the `app_user` row
   and its `app_user_credential` row, exposed as an ADMIN-only `DELETE /api/users/{id}` → 204
   (path de-versioned in Pass 7, #12).
   Flyway `V5` replaces the full unique username index with a **partial** one
   (`WHERE deleted_at IS NULL`) so a freed username can be reused. Covered by `UserServiceTest`
   (soft-delete + 404) and an integration test (hidden from `getAllUsers`/`findByUsername` + username
   freed). **Accepted tradeoff:** already-issued stateless access tokens stay valid until expiry (≤15
   min); a deleted user's refresh also yields a token that fails the audience check, since the token
   customizer's credential lookup now misses.

### B. Code design

5. `UserResponse` serializes the full `Role` JPA entity (drags `BaseEntity` audit fields into the API,
   lazy-load risk) — expose a `roleName` string. ✅ Done — Pass 3: `UserResponse.role` (the `Role`
   entity) replaced by a `roleName` string (`role.getName().name()`); the response no longer drags
   `BaseEntity` audit fields or risks a lazy-load on serialize.
6. `UserCredential.user` is `@ManyToOne(cascade = ALL)` but is conceptually one-to-one — model as
   `@OneToOne`. ✅ Done — Pass 3: now `@OneToOne(cascade = ALL)` (no schema change — same `user_id`
   FK column, both mappings default to EAGER fetch).
7. Repository finders return `null` (`findByUsername`, `findByName`) — return `Optional<>` and
   `orElseThrow` the missing-`USER`-role case in `AuthService`. ✅ Done — Pass 3:
   `UserCredentialRepository.findByUsername` and `RoleRepository.findByName` return `Optional<>`;
   callers updated (`CustomUserDetailsService` → `UsernameNotFoundException`, `RegistrationService`
   → `IllegalStateException` on missing `USER` role, the token customizer / `RoleInitializer` /
   `SuperAdminInitializer` use `Optional` directly).
8. `AuthService` no longer does auth (only registration, post-SAS) — rename to `RegistrationService`.
   ✅ Done — Pass 3: renamed `AuthService` → `RegistrationService` (and `AuthServiceTest` →
   `RegistrationServiceTest`); `AuthController` and `AuthControllerTest` updated. Method/endpoint
   names unchanged. While here, made the `AuthControllerTest` `@WebMvcTest` slice self-sufficient —
   it previously failed to start its context unless `jwt_jwks` was set in the env (the slice still
   binds `JwtProperties`); it now supplies a throwaway `jwt.jwk-set` via `@DynamicPropertySource`,
   mirroring `AuthserviceApplicationTests`.
9. Hygiene — ✅ Done — Pass 1: explicit imports in `UserController` (dropped the wildcard),
   `@Transactional(readOnly = true)` on `getAllUsers`, removed the redundant `lombok.Getter` import in
   `UserSignupRequest`, and `server.port` is now `${PORT:8081}`. ✅ Done — Pass 9: the leftover
   `status_enum` Postgres ENUM → `VARCHAR(20) + CHECK (status IN ('ACTIVE','INACTIVE'))` (the old #5.6
   nit) — Flyway `V6` converts the column on `app_role`/`app_user`/`app_user_credential` and drops the
   now-unused `status_enum` type; `BaseEntity` drops `@JdbcTypeCode(NAMED_ENUM)` (kept
   `@Enumerated(STRING)`, which maps the enum to the new `varchar`). Shipped with #16 (same migration).

### C. Spring Boot 4 / Spring Framework 7 features to adopt

10. RFC 7807 `ProblemDetail` for error bodies + a handler for `MethodArgumentNotValidException`
    (validation 400s currently fall through to the default `/error`). ✅ Done — Pass 6:
    `GlobalExceptionHandler` now extends `ResponseEntityExceptionHandler`, so every Spring MVC
    exception renders as a `ProblemDetail` (`application/problem+json`) instead of the default
    `/error` page. `ResponseStatusException` (the app's primary error signal) maps via
    `handleExceptionInternal` (its `getBody()` is already a `ProblemDetail` carrying status +
    reason-as-`detail`) — routed through the converters, **not** `sendError(...)`, preserving the
    Phase-1 fix that kept permit-all errors from being masked as 401. `handleMethodArgumentNotValid`
    is overridden to attach a field → message `errors` map so bean-validation 400s are actionable.
    `AuthControllerTest` updated to assert the new shape (`$.detail`, `$.errors.*`,
    `application/problem+json`) for the 400 and 409 paths.
11. ✅ Done — Pass 2: secrets load via Spring Boot config tree
    (`spring.config.import=configtree:${SECRETS_DIR:/run/secrets}/`); the `db_password` and
    `jwt_jwks` files map to `spring.datasource.password` / `jwt.jwk-set`. `SecretProvider` +
    the SpEL injection are gone; docker-compose secret mounts unchanged; local runs set `SECRETS_DIR`.
12. Native API versioning (`ApiVersionConfigurer`) instead of the hard-coded `/api/v1/` path. ✅ Done —
    Pass 7: adopted Spring Framework 7 native versioning. A `WebMvcConfiguration` (`WebMvcConfigurer`)
    configures `configureApiVersioning` to read the version from the **`X-API-Version`** header
    (`useRequestHeader`), supporting version `"1"`, **optional** (`setVersionRequired(false)`) with
    `setDefaultVersion("1")` so callers that omit the header keep working; an unsupported version is
    rejected with 400 (`InvalidApiVersionException`). The version moved out of the URL: `UserController`
    is now `/api/users` with `@GetMapping(version = "1")` / `@DeleteMapping(path = "/{id}", version = "1")`
    instead of the hard-coded `/api/v1/users`. The resource-server `securityMatcher` is `/api/**`
    (was `/api/v1/**`). **Contract change:** consumers move from `GET /api/v1/users` to `GET /api/users`
    (optionally `X-API-Version: 1`). Verified by the full `@SpringBootTest` boot (a version mapping with
    no `ApiVersionStrategy` would fail startup) + `protectedApiRequiresBearerToken` hitting `/api/users`.
    **Deferred:** asserting version *routing* with a real authenticated token — folds into #20 (token
    customizer end-to-end), the only place a valid token is minted.
13. Virtual threads — `spring.threads.virtual.enabled=true` (blocking JDBC + bcrypt on Java 25). ✅ Done —
    Pass 5: enabled `spring.threads.virtual.enabled`, so each request (and its blocking JDBC + bcrypt
    work) runs on a virtual thread instead of a pooled platform thread; throughput is no longer capped
    by the Tomcat worker pool.
14. JSpecify null-safety to replace the `Objects.isNull/nonNull` chains in the token customizer. ✅ Done —
    Pass 8: declared `org.jspecify:jspecify` explicitly (already transitive via `spring-core`, version
    managed by the Boot BOM) and marked `AuthorizationServerConfiguration` `@NullMarked`. The token
    customizer no longer imports `java.util.Objects`: the `Objects.isNull(principal) || ...` chain is now
    a `principal == null || principal.getName() == null` guard (kept defensively — SAS's
    `getPrincipal()`/`getName()` aren't JSpecify-annotated, so their nullness is unspecified), and the
    `findByUsername(...).orElse(null)` + `Objects.isNull(credential)` dance collapses into
    `Optional.ifPresent(credential -> ...)`.

### D. Java 25 / modeling

15. Records for immutable DTOs (`UserResponse`, `UserSignupRequest`) and the constructor-bound
    `@ConfigurationProperties` classes (`@DefaultValue`). ✅ Done — Pass 8: `UserResponse` and
    `UserSignupRequest` are now records (the JSON response shape and the `@NotBlank` request validation are
    unchanged — Jackson binds records via the canonical constructor and Hibernate Validator validates the
    components; the `createResponse` factory is preserved). `JwtProperties`, `RegisteredClientProperties`,
    and `SuperAdminProperties` are constructor-bound records: `@Validated` + the
    `@NotBlank`/`@NotNull`/`@NotEmpty` constraints carry over, `@DefaultValue` supplies the optional
    defaults (`accessTokenTtl=15m`, `refreshTokenTtl=24h`, super-admin `username`/`firstName`/`lastName`),
    and the `JwkSetConverter` (`@ConfigurationPropertiesBinding`) still binds `jwt.jwk-set` → `JWKSet`. All
    `getX()` call sites moved to the `x()` record accessors. Verified by the full `@SpringBootTest` boot
    (a binding/validation failure on any of the three records would fail startup) plus the slice/unit
    tests.
16. Spring Data JPA auditing (`@CreatedDate`/`@LastModifiedDate` + `@EnableJpaAuditing`) replacing the
    hand-rolled `@PrePersist`/`@PreUpdate`; switch audit timestamps to `Instant`/`timestamptz` (UTC). ✅ Done —
    Pass 9: a `JpaAuditingConfiguration` (`@EnableJpaAuditing`) + `@EntityListeners(AuditingEntityListener)` on
    `BaseEntity` now populate `createdAt`/`updatedAt` (annotated `@CreatedDate`/`@LastModifiedDate`); the
    hand-rolled `onCreate`/`onUpdate` callbacks are gone. The three audit fields moved `LocalDateTime` →
    `Instant` and the columns `TIMESTAMP` → `timestamptz` (Flyway `V6`, existing naive values read `AT TIME
    ZONE 'UTC'`), so timestamps are zone-unambiguous. Status is no longer stamped by a callback, so the field
    initializes to `Status.ACTIVE` (DB `DEFAULT 'ACTIVE'` kept as backstop); `softDelete()` uses `Instant.now()`.
    `ddl-auto` is `none` (Flyway-managed) so there's no Hibernate validation mismatch. Verified by the full
    `@SpringBootTest` boot (Flyway V1–V6 applies cleanly) + `UserServiceTest` soft-delete (`deletedAt` now
    `Instant`, asserted `isNotNull()`).

### E. Architecture / security (forward-looking)

17. Scheduled pruning of expired `oauth2_authorization` rows (the item explicitly deferred from
    Phase 1 #6 — SAS state otherwise grows forever).
18. Observability/ops: add `spring-boot-starter-actuator` (health/readiness probes for compose/k8s),
    `server.forward-headers-strategy` (correct issuer URL behind a proxy), and rate-limiting on `/login`
    and `/auth/signup`. ✅ Partially done — Pass 5: added `spring-boot-starter-actuator`, exposing **only**
    `health` over HTTP with liveness/readiness probes enabled (`management.endpoint.health.probes.enabled`);
    `/actuator/health/**` is permitted unauthenticated in the default chain (details stay hidden —
    `show-details` defaults to `never`) so compose/k8s probes work while no other actuator endpoint is on
    the network. Set `server.forward-headers-strategy=framework` so `X-Forwarded-*` (scheme/host/port) is
    honored behind a proxy/ingress. Covered by an integration test asserting the three health endpoints are
    public and `UP`. **Still pending:** rate-limiting on `/login` and `/auth/signup` (needs a filter/bucket
    library — deferred to its own pass).
19. Multi-project RBAC: many-to-many user↔roles↔authorities and per-client audience/scopes (resource
    indicators) instead of the single `@ManyToOne` role + single global `aud`.
20. Test the token customizer end-to-end (mint a real SAS token; assert `sub`=UUID / `role` / `aud`) —
    currently the most security-critical custom code is uncovered. ✅ Done — Pass 9: added
    `tokenCustomizerStampsSubRoleAndAudience()` to the full-app `@SpringBootTest` (reusing the Testcontainers
    context). It drives the real Authorization Code + PKCE flow over MockMvc — `GET /oauth2/authorize`
    (params in the query string, authenticated via `spring-security-test`'s `.with(user(...))`) → extract the
    code → `POST /oauth2/token` with client HTTP Basic + `code_verifier` → then parses the minted access token
    (Nimbus `SignedJWT`) and asserts `sub` = the user UUID, the `role` claim = `USER`, and `aud` contains
    `authservice`. Requests scope `profile roles` (not `openid`) — an ID token would need `auth_time`, which
    SAS derives from the SS7 `FactorGrantedAuthority` a real login carries but the synthetic test principal
    does not; the access token (the thing under test) needs none. Added `spring-security-test` as a test dep.
21. **Onboarding more OAuth2 clients without editing `application.yml`.** Today `oauth2-client.*`
    (`RegisteredClientProperties`) models exactly **one** client, used by `RegisteredClientInitializer`
    only to seed/reconcile `authservice-client` on startup. But clients are already persisted in Postgres
    via `JdbcRegisteredClientRepository` (Flyway `V3` → `oauth2_registered_client`), so anything that calls
    `registeredClientRepository.save(...)` adds a client at runtime (no restart). Options:
    - **A — SAS built-in OIDC Dynamic Client Registration (RFC 7591 / OIDC DCR).** Shipped by Spring
      Authorization Server but **disabled by default**: enable it via
      `oidc(o -> o.clientRegistrationEndpoint(Customizer.withDefaults()))` → `POST /connect/register`
      (also surfaces `registration_endpoint` in discovery). SAS generates `client_id`/`client_secret`,
      saves through the `RegisteredClientRepository`, and returns a `registration_access_token` +
      `registration_client_uri`. **Bootstrap requirement:** registration needs an initial access token with
      scope `client.create` (`client.read` to read back), minted via the **`client_credentials`** grant from
      a seeded "registrar" client — the current `authservice-client` can't do this (only
      `authorization_code` + `refresh_token`, scopes `openid/profile/roles`). Best for self-service /
      third-party onboarding; larger attack surface, secret shown once.
    - **B — Admin-guarded endpoint (recommended for a controlled set of internal projects).** A small
      `POST /api/clients` controller `@PreAuthorize("hasRole('ADMIN')")` that builds a `RegisteredClient`
      and calls `registeredClientRepository.save(...)`. Reuses the existing ADMIN role/method security,
      no `client_credentials` bootstrap, full control over allowed grants/scopes/redirects.
    - **C — Multiple seeded clients via config.** Refactor `RegisteredClientProperties` → `List<…>` and
      loop in the initializer. Fine for a small, static set, but still "edit + redeploy".
    **Caveat (ties to #19):** the token customizer stamps `aud = jwt.audience` (single global value) on
    *every* access token regardless of client, so however clients are added they currently all share one
    audience. Pair whichever option with #19 (per-client audience/scopes) for real multi-project isolation.

---

## Appendix — `authservice` vs. Keycloak (build-vs-adopt note)

A standalone strategic note (not a work item): how this server compares to **Keycloak**, what the gaps
are, and whether the long-term plan should be to keep building `authservice` or adopt Keycloak. References
point at the code as it exists today.

### Framing

`authservice` is a genuinely **standards-compliant OAuth2/OIDC Authorization Server**, not a toy. The
hard, easy-to-get-wrong protocol core is done and tested. Keycloak's advantage is **not** the protocol —
it is the enormous surround (MFA, social/enterprise login, admin UI, self-service account flows,
brute-force protection, multi-tenancy) plus a security team tracking CVEs.

### What `authservice` already matches Keycloak on

These are real and working, not aspirations:

- **Authorization Code + PKCE** grant, refresh-token rotation (`reuseRefreshTokens(false)`),
  `/oauth2/revoke` (`AuthorizationServerConfiguration`).
- **JWKS endpoint + OIDC discovery** (`/.well-known/openid-configuration`) — consumers validate offline.
- **Stateless RS256 access tokens** with `kid`, issuer + audience validation on the resource server
  (`SecurityConfiguration.jwtDecoder`).
- **Tailored token contract** — `sub` = user UUID, `role` claim, controlled `aud`; no Keycloak claim
  bloat (`jwtTokenCustomizer`).
- **JDBC-persisted clients & authorizations** (Flyway `V3`) — restart-safe, horizontally scalable.
- Key-rotation design (prepend-to-set), soft-delete, JPA auditing, virtual threads, actuator health.

For a single app or a small set of first-party services this is sufficient — and arguably nicer than
Keycloak (no realm/client/mapper config sprawl, no second platform to operate).

### The big gaps — what Keycloak gives "for free" that we'd have to build

Each is weeks-to-months of security-critical work to do well:

| Capability | `authservice` today | Keycloak |
|---|---|---|
| MFA / passkeys | ✗ none | TOTP, WebAuthn, recovery codes, step-up |
| Social / enterprise login | ✗ none | Google/GitHub/SAML/OIDC brokering |
| User federation | ✗ Postgres only | LDAP / AD / Kerberos |
| Admin UI + Admin REST API | ✗ (one ADMIN endpoint) | Full console (users, clients, roles, sessions) |
| Self-service account flows | ✗ signup only | Password reset, email verification, profile, sessions |
| Email / SMTP flows | ✗ none | Verification, reset, magic links, templates |
| Brute-force / lockout / rate-limit | ✗ deferred (#18) | Built-in detection + temporary lockout |
| Multi-tenancy | ✗ single global `aud`, one realm | Realms = isolated user/client populations |
| RBAC depth | Single `@ManyToOne` role (`User.java`) | Composite/client roles, groups, scopes |
| Protocols | OIDC/OAuth2 only | + SAML 2.0, token exchange, CIBA, device flow |
| Sessions | Stateless; no pre-expiry access-token revoke | Server-side sessions, SLO, cross-device revoke |
| Audit / events | App logs only | Login + admin event streams |
| Security maintenance | We own every CVE | Dedicated security team + CVE pipeline |

Items **#19** (per-client audience/scopes) and **#21** (client onboarding) above are precisely the
multi-tenant/admin features Keycloak ships by default.

### Near-term improvements if we keep building (priority order)

1. **Brute-force / rate-limiting on `/login` and `/auth/signup`** (#18, pending) — biggest *security*
   gap vs Keycloak: an unthrottled password endpoint.
2. **Account lifecycle flows** — password reset + email verification (most-used "boring" features we
   lack; error-prone to hand-roll — lean on Spring primitives).
3. **`client_credentials` grant + a seeded service/registrar client** — service-to-service auth; also
   unblocks self-service client registration (#21 option A).
4. **Per-client audience & scopes (#19)** — today one global `aud` on every token
   (`AuthorizationServerConfiguration` customizer), so clients can't be isolated.
5. **Scheduled pruning of `oauth2_authorization` (#17)** — SAS state grows forever otherwise.
6. **Richer RBAC** — move off single `@ManyToOne` role to roles↔authorities (#19).
7. **Admin endpoints / minimal UI** for users & clients (#21 option B), guarded by the ADMIN role.
8. **MFA (TOTP)** — to seriously close the Keycloak gap; large effort.

Items 1–3 matter most before any real user touches this.

### Long-term plan: keep building vs adopt Keycloak

Decide by **who the users are and how many tenants/IdPs are needed**.

- **Keep `authservice`** if it's a portfolio/learning project (huge educational ROI, hard part is done),
  **or** it serves a small set of **first-party** apps with **local** username/password identity and
  simple RBAC. Keycloak would be overkill — a second platform to operate for unused features.
- **Adopt Keycloak** (or a managed service — Auth0 / Entra ID / Cognito) once **any** of these is a real
  requirement: MFA/passkeys, **social or enterprise (LDAP/SAML) login**, multi-tenant isolation, a
  non-developer admin UI, or self-service account recovery at scale. Reimplementing those means owning
  authentication security indefinitely (every CVE, every recovery edge case, every lockout bypass) —
  exactly where homegrown auth servers fail in production.

**Recommended stance: migrate, not rebuild.** Keep `authservice` as today's issuer. If we hit a Keycloak
trigger, two options preserve the work instead of throwing it away:

- **Front Keycloak with our token contract** — Keycloak owns users/MFA/social; a thin layer keeps claims
  in the shape our services expect; or
- **Make `authservice` an OIDC client that brokers to Keycloak** as an upstream IdP.

Either way the standards-compliant core survives; we stop re-implementing Keycloak's surround. **Line in
the sand:** the first hard requirement for MFA / social login / multi-tenant / admin-UI is the signal to
adopt Keycloak rather than extend `authservice` further.
