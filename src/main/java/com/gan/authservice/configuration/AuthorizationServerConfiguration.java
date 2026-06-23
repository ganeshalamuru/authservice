package com.gan.authservice.configuration;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;

import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.repository.UserCredentialRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Spring Authorization Server wiring: the standard /oauth2/authorize, /oauth2/token, /oauth2/jwks,
 * /oauth2/revoke and OIDC discovery endpoints, persisted to Postgres via the JDBC services.
 */
@Configuration
public class AuthorizationServerConfiguration {

    /**
     * Authorization-server endpoint chain (highest precedence). Enables OIDC and redirects
     * unauthenticated browser requests at /oauth2/authorize to the form-login page.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            new OAuth2AuthorizationServerConfigurer();
        http
            .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            .with(authorizationServerConfigurer, authorizationServer ->
                authorizationServer.oidc(Customizer.withDefaults()))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers(authorizationServerConfigurer.getEndpointsMatcher()))
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(JwtProperties jwtProperties) {
        return AuthorizationServerSettings.builder().issuer(jwtProperties.getIssuer()).build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    /**
     * Preserves the token contract from IMPROVEMENTS.md #1-#4: sub = user UUID, a "role" claim on the
     * access token, and aud = jwt.audience so the resource server's audience validator still passes.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(JwtProperties jwtProperties,
        UserCredentialRepository userCredentialRepository) {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (Objects.isNull(principal) || Objects.isNull(principal.getName())) {
                return;
            }
            UserCredential credential = userCredentialRepository.findByUsername(principal.getName())
                .orElse(null);
            if (Objects.isNull(credential)) {
                return;
            }
            context.getClaims().subject(credential.getUser().getId().toString());
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                // Derive the role from the user record (source of truth) rather than the login
                // Authentication, whose authorities also carry SS7 factor authorities (FACTOR_PASSWORD).
                String role = credential.getUser().getRole().getName().name();
                // Use a mutable ArrayList (not List.of) so the Authorization Server's JSON store can
                // deserialize the claim — its Jackson allowlist rejects java.util.ImmutableCollections.
                context.getClaims().audience(new ArrayList<>(List.of(jwtProperties.getAudience())));
                context.getClaims().claim(JWT_AUTHORITIES_CLAIM_NAME, role);
            }
        };
    }

}
