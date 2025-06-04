package com.gan.authservice.configuration;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SecretProvider {

    @Value("${spring.datasource.password.file}")
    private String dbPasswordPath;
    @Value("${jwt.private.key.file}")
    private String jwtPrivateKeyPath;
    @Getter
    private String dbPassword;
    @Getter
    private String jwtPrivateKey;

    @PostConstruct
    private void initialize() {
        try {
            dbPassword = Files.readString(Path.of(dbPasswordPath)).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read DB password", e);
        }
        try {
            jwtPrivateKey = Files.readString(Path.of(jwtPrivateKeyPath)).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read jwt private key", e);
        }
    }

}