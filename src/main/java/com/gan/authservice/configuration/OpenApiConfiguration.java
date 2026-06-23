package com.gan.authservice.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the Spring Authorization Server OAuth2 flow to Swagger UI. The SAS endpoints
 * (/oauth2/authorize, /oauth2/token, ...) are servlet filters, not MVC controllers, so springdoc
 * cannot discover them by scanning; instead we declare an {@code oauth2} security scheme so the
 * Swagger "Authorize" button can drive the Authorization Code + PKCE login → token exchange.
 */
@Configuration
public class OpenApiConfiguration {

    public static final String OAUTH2_SCHEME = "oauth2";
    public static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Base URL Swagger UI uses for the OAuth2 endpoints. It must be the same host the UI is served
     * from (so the token POST is same-origin and not blocked by CORS) and must use 127.0.0.1, since
     * Spring Authorization Server rejects {@code localhost} as a redirect host.
     */
    @Value("${swagger.oauth2.server-url}")
    private String oauthServerUrl;

    @Bean
    public OpenAPI authServiceOpenAPI() {
        OAuthFlow authorizationCodeFlow = new OAuthFlow()
            .authorizationUrl(oauthServerUrl + "/oauth2/authorize")
            .tokenUrl(oauthServerUrl + "/oauth2/token")
            .scopes(new Scopes()
                .addString("openid", "OpenID Connect scope")
                .addString("profile", "User profile")
                .addString("roles", "User roles"));

        SecurityScheme oauth2 = new SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .description("Authorization Code + PKCE via Spring Authorization Server")
            .flows(new OAuthFlows().authorizationCode(authorizationCodeFlow));

        SecurityScheme bearer = new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT")
            .description("Paste a raw access token");

        return new OpenAPI()
            .info(new Info().title("Auth Service API").version("v1"))
            .components(new Components()
                .addSecuritySchemes(OAUTH2_SCHEME, oauth2)
                .addSecuritySchemes(BEARER_SCHEME, bearer));
    }
}
