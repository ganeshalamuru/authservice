package com.gan.authservice.controller.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gan.authservice.service.security.RegistrationService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller slice for {@link AuthController}. Security filters are disabled ({@code addFilters =
 * false}) so the slice exercises request mapping, bean validation, and the {@code GlobalExceptionHandler}
 * advice in isolation from the three security filter chains.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final String VALID_BODY =
        "{\"username\":\"alice\",\"password\":\"s3cret\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RegistrationService registrationService;

    /**
     * The slice still binds {@code JwtProperties} (registered via {@code @EnableConfigurationProperties}
     * on the application class), so {@code jwt.jwk-set} must parse for the context to start even though
     * this slice never signs a token. Supply a throwaway signing key, as {@code AuthserviceApplicationTests}
     * does, rather than depending on a {@code jwt_jwks} env var / secret being present.
     */
    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        try {
            String jwkSetJson = generateSigningJwkSet();
            registry.add("jwt.jwk-set", () -> jwkSetJson);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to provision test signing key", e);
        }
    }

    @Test
    void signup_returns201OnValidRequest() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isCreated())
            .andExpect(content().string("Successfully signed up"));
        verify(registrationService).createUser(any());
    }

    @Test
    void signup_returns400ProblemDetailWhenRequiredFieldsBlank() throws Exception {
        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Request validation failed"))
            .andExpect(jsonPath("$.errors.username").exists())
            .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void signup_returns409ProblemDetailWhenServiceReportsDuplicate() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists"))
            .when(registrationService).createUser(any());

        mockMvc.perform(post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("Username already exists"));
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
        return "{\"keys\":[" + rsaKey.toJSONString() + "]}";
    }
}
