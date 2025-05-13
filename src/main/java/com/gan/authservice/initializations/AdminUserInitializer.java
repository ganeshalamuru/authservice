package com.gan.authservice.initializations;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    private static final String ADMIN_USER = "admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(String... args) {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN);
        User adminUser = new User(ADMIN_USER,
            passwordEncoder.encode(environment.getRequiredProperty("ADMIN_PASSWORD")), adminRole);
        adminUser.setFirstName(ADMIN_USER);
        adminUser.setLastName(ADMIN_USER);
        adminUser.setMetaData();
        try {
            userRepository.save(adminUser);
        } catch (Exception e) {
        }
    }
}
