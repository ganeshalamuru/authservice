package com.gan.authservice.constants;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bootstraps a super-admin (ADMIN) account on startup via {@code SuperAdminInitializer}. The password
 * is supplied through {@code SUPER_ADMIN_PASSWORD} and never hard-coded; when it is blank, seeding is
 * skipped so local runs without the secret still start.
 */
@Data
@ConfigurationProperties(prefix = "super-admin")
public class SuperAdminProperties {
    private String username = "superadmin";
    private String password;
    private String firstName = "Super";
    private String lastName = "Admin";
}
