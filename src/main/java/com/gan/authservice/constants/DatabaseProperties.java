package com.gan.authservice.constants;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.datasource")
public class DatabaseProperties {
    @NotBlank
    private String url;
    private String driverClassName;
    private String username;
}
