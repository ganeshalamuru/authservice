package com.gan.authservice.constants;

import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;
    @NotBlank
    private String audience;
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    /**
     * RS256 signing keys as a JWK Set. Bound from {@code jwt.jwk-set} (a config-tree secret holding
     * JWK Set JSON) via {@code JwkSetConverter}. The first key signs; the rest stay available so
     * their still-valid tokens keep verifying during a rotation overlap window.
     */
    @NotNull
    private JWKSet jwkSet;
}
