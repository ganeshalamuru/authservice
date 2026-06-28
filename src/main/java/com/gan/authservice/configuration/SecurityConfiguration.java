package com.gan.authservice.configuration;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;

import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.demo.OAuth2DemoProperties;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
public class SecurityConfiguration {

    /**
     * Stateless resource-server chain: validates the SAS-issued access tokens (signature + iss + aud)
     * for the protected API. Ordered after the authorization-server chain.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("USER", "ADMIN"))
            .oauth2ResourceServer(configurer -> configurer.jwt(jwt -> jwt
                .decoder(decoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }

    /**
     * Default chain: form login backs the OAuth2 authorization-code flow (SAS redirects unauthenticated
     * users here), plus the public signup/docs endpoints. Session based.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
        OAuth2DemoProperties demoProperties) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/auth/signup", "/login", "/error").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    // Liveness/readiness probes for compose/k8s; health details stay hidden (show-details
                    // defaults to never), and no other actuator endpoint is exposed over HTTP.
                    .requestMatchers("/actuator/health/**").permitAll()
                    // Chrome DevTools auto-probes this while you're on /login; permit it so it 404s
                    // quietly instead of being captured as the saved request and hijacking the
                    // post-login redirect away from /oauth2/authorize.
                    .requestMatchers("/.well-known/appspecific/**").permitAll();
                // OAuth2 flow visualizer (dev-only): expose the page + replay endpoint only when the
                // demo is enabled, so they stay invisible (the page requires auth, /demo 404s) in prod.
                if (demoProperties.enabled()) {
                    auth.requestMatchers("/demo/**", "/oauth2-demo.html").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .formLogin(form -> form.permitAll());
        return http.build();
    }

    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults(""); // Remove the ROLE_ prefix
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName(JWT_AUTHORITIES_CLAIM_NAME);
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix("");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Signing-key source used by Spring Authorization Server to mint and serve JWTs (/oauth2/jwks).
     * The encoder signs with the first key in the set, so prepend a freshly generated key to rotate;
     * older keys stay in the set (and on /oauth2/jwks) so their still-valid tokens keep verifying
     * during the overlap window. (Future: back this set with the DB for automated rotation.)
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(JwtProperties jwtProperties) {
        return new ImmutableJWKSet<>(jwtProperties.jwkSet());
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, JwtProperties jwtProperties) {
        // Validate against the whole set, matched by the token's kid, so a token signed by any key
        // currently in the set (including a pre-rotation key) still verifies.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtProperties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            audience -> audience != null && audience.contains(jwtProperties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator));
        return decoder;
    }

}
