package com.gan.authservice.initializer;

import com.gan.authservice.constants.SuperAdminProperties;
import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Seeds an ADMIN super-user on startup (idempotent), making the ADMIN authority referenced by the
 * security rules actually reachable. Runs after {@link RoleInitializer} (which seeds the ADMIN role)
 * via {@code @Order(2)}, and skips quietly when {@code SUPER_ADMIN_PASSWORD} is unset so local runs
 * without the secret still start.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class SuperAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final SuperAdminProperties properties;
    private final RoleRepository roleRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(properties.password())) {
            log.warn("SUPER_ADMIN_PASSWORD is not set; skipping super-admin seeding");
            return;
        }
        if (userCredentialRepository.existsByUsername(properties.username())) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
            .orElseThrow(() -> new IllegalStateException(
                "ADMIN role not seeded; RoleInitializer must run first"));
        User user = new User(properties.firstName(), properties.lastName(), adminRole);
        UserCredential credential = new UserCredential(
            user, properties.username(), passwordEncoder.encode(properties.password()));
        userCredentialRepository.save(credential);
        log.info("Seeded super-admin user '{}'", properties.username());
    }
}
