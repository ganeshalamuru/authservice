package com.gan.authservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end integration test: boots the full application against a real PostgreSQL container so
 * Flyway (V1–V4) actually runs and the SAS client/role initializers seed Postgres. A throwaway RSA
 * keypair, the container password, and the base64 private key are wired in via
 * {@link DynamicPropertySource} (config tree is skipped when SECRETS_DIR is absent), so the app's
 * datasource + key beans start exactly as in production — without committing any secrets.
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

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        try {
            KeyPair keyPair = generateRsaKeyPair();
            Path publicKeyFile = tempFile("jwt-public", publicKeyPem(keyPair.getPublic()));
            // The app expects jwt.private.key to hold base64(PEM); SecurityConfiguration base64-decodes
            // it and RsaKeyConverters.pkcs8() then parses the PEM. Config tree is skipped in tests, so
            // these @DynamicPropertySource values supply the datasource password and signing key directly.
            String privateKeyFileContent = Base64.getEncoder()
                .encodeToString(privateKeyPem(keyPair.getPrivate()).getBytes(StandardCharsets.UTF_8));

            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("jwt.private.key", () -> privateKeyFileContent);
            registry.add("jwt.public.key", () -> publicKeyFile.toUri().toString());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Failed to provision test secrets", e);
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
        mockMvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
    }

    private static Path tempFile(String prefix, String content) throws IOException {
        Path file = Files.createTempFile(prefix, ".tmp");
        file.toFile().deleteOnExit();
        Files.writeString(file, content);
        return file;
    }

    private static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicKeyPem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
            + "\n-----END PUBLIC KEY-----\n";
    }

    private static String privateKeyPem(PrivateKey key) {
        return "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
    }
}
