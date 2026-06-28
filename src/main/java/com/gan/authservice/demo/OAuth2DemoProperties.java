package com.gan.authservice.demo;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Toggle for the OAuth2 flow visualizer ({@code POST /demo/oauth2/run} + {@code /oauth2-demo.html}).
 * Disabled by default: the replay endpoint drives logins with supplied credentials and reveals the
 * resulting tokens, so it must never be reachable in production. Enable for local/dev runs via
 * {@code DEMO_OAUTH2_ENABLED=true}. When disabled the controller bean is not registered and the
 * endpoint simply 404s.
 *
 * @param enabled whether the demo controller is registered; defaults to {@code false}.
 */
@NullMarked
@ConfigurationProperties(prefix = "demo.oauth2")
public record OAuth2DemoProperties(@DefaultValue("false") boolean enabled) {
}
