package com.gan.authservice.demo;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoint behind {@code /oauth2-demo.html}: replays the OAuth2 Authorization Code + PKCE
 * flow and returns every interaction as an ordered list. Registered only when
 * {@code demo.oauth2.enabled=true}; otherwise the path 404s (prod-safe). See {@link OAuth2DemoService}.
 */
@NullMarked
@RestController
@RequestMapping("/demo/oauth2")
@ConditionalOnProperty(prefix = "demo.oauth2", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OAuth2DemoController {

    private final OAuth2DemoService demoService;

    @PostMapping("/run")
    public List<InteractionStep> run(@Valid @RequestBody DemoRunRequest request) {
        return demoService.run(request);
    }
}
