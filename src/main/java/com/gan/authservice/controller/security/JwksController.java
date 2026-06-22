package com.gan.authservice.controller.security;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the signing keys as a JWK Set so resource servers can validate the self-issued JWTs
 * offline and pick up key rotation automatically. Only the public portion of each key is served.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JWKSet jwkSet;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> keys() {
        return jwkSet.toPublicJWKSet().toJSONObject();
    }

}
