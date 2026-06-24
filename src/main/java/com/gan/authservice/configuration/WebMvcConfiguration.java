package com.gan.authservice.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Native Spring MVC API versioning (Spring Framework 7) — replaces the hard-coded {@code /api/v1/}
 * path. The version travels in the {@code X-API-Version} request header, so controllers declare the
 * version they serve with {@code @RequestMapping(version = ...)} (see {@code UserController}) while
 * the URL stays clean ({@code /api/users}).
 *
 * <p>The version is <strong>optional</strong> and defaults to {@code "1"}, so callers that omit the
 * header keep working; a header naming an unsupported version is rejected with 400
 * ({@code InvalidApiVersionException}).
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader("X-API-Version")
            .addSupportedVersions("1")
            .setVersionRequired(false)
            .setDefaultVersion("1");
    }
}
