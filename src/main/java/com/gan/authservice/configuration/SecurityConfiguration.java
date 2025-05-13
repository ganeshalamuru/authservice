package com.gan.authservice.configuration;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;

import com.gan.authservice.service.security.UsernamePasswordJwtTokenAuthenticationFilter;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.util.Base64;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", in = SecuritySchemeIn.HEADER)
public class SecurityConfiguration {

    @Value("${jwt.secret.key}")
    private String JWT_SECRET_KEY;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
        AuthenticationManager authenticationManager) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests((auth) -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/api/v1/**").hasAnyRole("USER","ADMIN")
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
            )
            .oauth2ResourceServer(configurer ->
                configurer.jwt(
                    jwtConfigurer -> jwtConfigurer.authenticationManager(authenticationManager)))
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            ).addFilterBefore(authenticationFilter(authenticationManager),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private UsernamePasswordJwtTokenAuthenticationFilter authenticationFilter(
        AuthenticationManager authenticationManager) {
        return new UsernamePasswordJwtTokenAuthenticationFilter(authenticationManager);
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
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
            jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    @Bean
    public JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder jwtDecoder) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
        List<AuthenticationProvider> authenticationProviderList) {
        return new ProviderManager(authenticationProviderList);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwsHeader jwsHeader() {
        return JwsHeader.with(MacAlgorithm.HS512).build();
    }

    //JWK
    @Bean
    public OctetSequenceKey octetSequenceKey() {
        byte[] decodedKey = Base64.from(JWT_SECRET_KEY).decode();
        SecretKey secretKey = new SecretKeySpec(decodedKey, "NONE");
        return new OctetSequenceKey.Builder(secretKey)
            .algorithm(JWSAlgorithm.HS512)
            .issueTime(Date.from(Instant.now()))
            .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JWK jwk) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    public JwtDecoder jwtDecoder(OctetSequenceKey octetSequenceKey) {
        return NimbusJwtDecoder.withSecretKey(octetSequenceKey.toSecretKey())
            .macAlgorithm(MacAlgorithm.HS512).build();
    }

}
