package com.gan.authservice.constants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the OAuth2 client that Spring Authorization Server seeds on startup
 * ({@code RegisteredClientInitializer}). The client secret is supplied via env var and never
 * hard-coded.
 *
 * @param clientId        the client identifier.
 * @param clientSecret    the client secret (encoded before persistence).
 * @param redirectUris    allowed redirect URIs.
 * @param refreshTokenTtl refresh-token lifetime; defaults to 24 hours.
 */
@Validated
@ConfigurationProperties(prefix = "oauth2-client")
public record RegisteredClientProperties(
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotEmpty List<String> redirectUris,
    @NotNull @DefaultValue("24h") Duration refreshTokenTtl) {
}
