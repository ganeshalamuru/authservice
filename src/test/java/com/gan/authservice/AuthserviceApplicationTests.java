package com.gan.authservice;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gan.authservice.constants.RegisteredClientProperties;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.service.UserService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test: boots the full application against a real PostgreSQL container so
 * Flyway (V1–V5) actually runs and the SAS client/role initializers seed Postgres. A throwaway RSA
 * signing key (as a JWK Set) and the container password are wired in via {@link DynamicPropertySource}
 * (config tree is skipped when SECRETS_DIR is absent), so the app's datasource + key beans start
 * exactly as in production — without committing any secrets.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthserviceApplicationTests {

    @Container
    static final PostgreSQLContainer POSTGRES =
        new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCredentialRepository userCredentialRepository;

    @Autowired
    private RegisteredClientProperties clientProperties;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        try {
            // jwt.jwk-set expects JWK Set JSON with private params; JwkSetConverter parses it and the
            // Authorization Server signs/serves with it. Config tree is skipped in tests, so these
            // @DynamicPropertySource values supply the datasource password and signing key directly.
            String jwkSetJson = generateSigningJwkSet();

            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("jwt.jwk-set", () -> jwkSetJson);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to provision test signing key", e);
        }
    }

    @Test
    void openIdDiscoveryIsPublic() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issuer").exists())
            .andExpect(jsonPath("$.jwks_uri").exists());
    }

    @Test
    void jwksEndpointExposesSigningKey() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.keys[0].kid").exists())
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"));
    }

    @Test
    void signupPersistsUserAndRejectsDuplicate() throws Exception {
        String body = "{\"username\":\"intuser\",\"password\":\"s3cret\","
            + "\"firstName\":\"Test\",\"lastName\":\"User\"}";

        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        // The unique index (V2) + pre-check reject the second signup with the same username.
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void protectedApiRequiresBearerToken() throws Exception {
        // Path no longer carries the version (now the X-API-Version header, default "1"); the
        // resource-server chain still matches /api/** and rejects the missing bearer token.
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbesArePublicAndUp() throws Exception {
        // Permitted unauthenticated for compose/k8s; details stay hidden (show-details: never).
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void softDeleteHidesUserFromQueriesAndFreesUsername() throws Exception {
        String body = "{\"username\":\"softdeluser\",\"password\":\"s3cret\","
            + "\"firstName\":\"Soft\",\"lastName\":\"Delete\"}";
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        UUID userId = userCredentialRepository.findByUsername("softdeluser").orElseThrow()
            .getUser().getId();
        // Visible before deletion.
        assertThat(userService.getAllUsers())
            .anyMatch(user -> user.id().equals(userId.toString()));

        userService.deleteUser(userId);

        // @SQLRestriction hides both rows: gone from listings and the credential lookup misses
        // (so the user can no longer authenticate).
        assertThat(userService.getAllUsers())
            .noneMatch(user -> user.id().equals(userId.toString()));
        assertThat(userCredentialRepository.findByUsername("softdeluser")).isEmpty();

        // The partial unique index (V5) frees the name, so it can be registered again.
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    @Test
    void tokenCustomizerStampsSubRoleAndAudience() throws Exception {
        // Register a user; RegistrationService assigns the USER role.
        String body = "{\"username\":\"tokuser\",\"password\":\"s3cret\","
            + "\"firstName\":\"Token\",\"lastName\":\"User\"}";
        mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
        UUID userId = userCredentialRepository.findByUsername("tokuser").orElseThrow().getUser().getId();

        // PKCE: code_challenge = BASE64URL(SHA-256(code_verifier)), method S256.
        String codeVerifier = randomUrlSafe(32);
        String codeChallenge = base64Url(sha256(codeVerifier));
        String redirectUri = clientProperties.redirectUris().getFirst();

        // Authorize as the user in one shot (consent is disabled for this client) -> 302 to the
        // redirect_uri carrying the authorization code. SAS reads these from the query string
        // (request.getQueryString()), so they must live in the URL, not MockMvc .param().
        URI authorizeUri = UriComponentsBuilder.fromPath("/oauth2/authorize")
            .queryParam("response_type", "code")
            .queryParam("client_id", clientProperties.clientId())
            .queryParam("redirect_uri", redirectUri)
            // Request only access-token scopes (no "openid"): an OIDC ID token would need auth_time,
            // which SAS derives from the SS7 FactorGrantedAuthority a real form login carries but the
            // synthetic .with(user(...)) here does not. The access token (asserted below) needs none.
            .queryParam("scope", "profile roles")
            .queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256")
            .queryParam("state", randomUrlSafe(16))
            .build().encode().toUri();
        MvcResult authorize = mockMvc.perform(get(authorizeUri).with(user("tokuser")))
            .andExpect(status().is3xxRedirection())
            .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).as("redirect to the client callback").isNotNull();
        String code = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
        assertThat(code).as("authorization code in the callback redirect").isNotNull();

        // Exchange the code (with client Basic auth + the original verifier) for tokens.
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(clientProperties.clientId(), clientProperties.clientSecret()))
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("code_verifier", codeVerifier))
            .andExpect(status().isOk())
            .andReturn();
        String accessToken = JsonPath.read(token.getResponse().getContentAsString(), "$.access_token");

        // The token customizer must stamp sub = user UUID, the role claim, and the configured audience.
        JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getStringClaim(JWT_AUTHORITIES_CLAIM_NAME)).isEqualTo("USER");
        assertThat(claims.getAudience()).contains("authservice");
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return base64Url(buffer);
    }

    private static byte[] sha256(String value) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A single-key JWK Set (private params included), matching the shape of the jwt_jwks secret. */
    private static String generateSigningJwkSet() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey((RSAPrivateKey) keyPair.getPrivate())
            .keyID(UUID.randomUUID().toString())
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build();
        // RSAKey.toJSONString() includes the private params since they are present on the key.
        return "{\"keys\":[" + rsaKey.toJSONString() + "]}";
    }
}
