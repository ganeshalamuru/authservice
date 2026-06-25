package com.gan.authservice.constants;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bootstraps a super-admin (ADMIN) account on startup via {@code SuperAdminInitializer}. The password
 * is supplied through {@code SUPER_ADMIN_PASSWORD} and never hard-coded; when it is blank, seeding is
 * skipped so local runs without the secret still start.
 *
 * @param username  the super-admin username; defaults to {@code superadmin}.
 * @param password  the super-admin password; unset (null) skips seeding.
 * @param firstName the super-admin first name; defaults to {@code Super}.
 * @param lastName  the super-admin last name; defaults to {@code Admin}.
 */
@ConfigurationProperties(prefix = "super-admin")
public record SuperAdminProperties(
    @DefaultValue("superadmin") String username,
    String password,
    @DefaultValue("Super") String firstName,
    @DefaultValue("Admin") String lastName) {
}
