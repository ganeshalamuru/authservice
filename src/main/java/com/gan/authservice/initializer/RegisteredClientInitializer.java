package com.gan.authservice.initializer;

import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.constants.RegisteredClientProperties;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Seeds the Authorization Code + PKCE client into Postgres on startup (idempotent), mirroring
 * {@link RoleInitializer}. The client requires PKCE, uses self-contained (JWT) access tokens, and
 * rotates refresh tokens so they can be revoked before access-token expiry.
 */
@Component
@RequiredArgsConstructor
public class RegisteredClientInitializer implements CommandLineRunner {

    private final RegisteredClientRepository registeredClientRepository;
    private final RegisteredClientProperties clientProperties;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (Objects.nonNull(registeredClientRepository.findByClientId(clientProperties.getClientId()))) {
            return;
        }
        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientProperties.getClientId())
            .clientSecret(passwordEncoder.encode(clientProperties.getClientSecret()))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope("roles")
            .clientSettings(ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                .accessTokenTimeToLive(jwtProperties.getAccessTokenTtl())
                .refreshTokenTimeToLive(clientProperties.getRefreshTokenTtl())
                .reuseRefreshTokens(false)
                .build());
        clientProperties.getRedirectUris().forEach(client::redirectUri);
        registeredClientRepository.save(client.build());
    }
}
