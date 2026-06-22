package com.gan.authservice.constants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the OAuth2 client that Spring Authorization Server seeds on startup
 * ({@code RegisteredClientInitializer}). The client secret is supplied via env var and never
 * hard-coded.
 */
@Data
@ConfigurationProperties(prefix = "oauth2-client")
public class RegisteredClientProperties {
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
    @NotEmpty
    private List<String> redirectUris;
    @NotNull
    private Duration refreshTokenTtl = Duration.ofHours(24);
}
