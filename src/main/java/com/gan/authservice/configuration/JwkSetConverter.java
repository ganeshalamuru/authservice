package com.gan.authservice.configuration;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.text.ParseException;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the inline {@code jwt.jwk-set} JSON (a config-tree secret) into a Nimbus {@link JWKSet}.
 * Fails fast at startup if the set carries no key with private parameters, so a public-only set
 * can't silently leave the Authorization Server unable to sign tokens.
 */
@Component
@ConfigurationPropertiesBinding
public class JwkSetConverter implements Converter<String, JWKSet> {

    @Override
    public JWKSet convert(String source) {
        JWKSet jwkSet;
        try {
            jwkSet = JWKSet.parse(source);
        } catch (ParseException e) {
            throw new IllegalArgumentException("jwt.jwk-set is not valid JWK Set JSON", e);
        }
        boolean hasSigningKey = jwkSet.getKeys().stream().anyMatch(JWK::isPrivate);
        if (!hasSigningKey) {
            throw new IllegalArgumentException(
                "jwt.jwk-set must contain at least one key with private parameters for signing");
        }
        return jwkSet;
    }
}
