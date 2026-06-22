package com.gan.authservice.constants;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank
    private String issuer;
    @NotBlank
    private String audience;
}
