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
4. **Decide the token model** (stateless+JWKS vs introspection) — unblocks everything else.
5. **Adopt Spring Authorization Server** (refresh tokens, JWKS, clients) — larger effort.
6. **DB hardening** (FK indexes, hashed tokens, token cleanup).
7. **Code hygiene** (split services, global error handler, tests, remove cruft).
