package com.gan.authservice.constants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;
    @NotBlank
    private String audience;
    @NotNull
    private Duration accessTokenTtl = Duration.ofMinutes(15);
}
