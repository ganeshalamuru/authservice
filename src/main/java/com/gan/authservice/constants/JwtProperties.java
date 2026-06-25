package com.gan.authservice.constants;

import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param issuer         the token issuer URL (= the {@code AuthorizationServerSettings} issuer).
 * @param audience       the {@code aud} claim the resource server validates.
 * @param accessTokenTtl access-token lifetime; defaults to 15 minutes.
 * @param jwkSet         RS256 signing keys as a JWK Set. Bound from {@code jwt.jwk-set} (a
 *                       config-tree secret holding JWK Set JSON) via {@code JwkSetConverter}. The
 *                       first key signs; the rest stay available so their still-valid tokens keep
 *                       verifying during a rotation overlap window.
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    @NotBlank String issuer,
    @NotBlank String audience,
    @NotNull @DefaultValue("15m") Duration accessTokenTtl,
    @NotNull JWKSet jwkSet) {
}
