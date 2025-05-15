package com.gan.authservice.initializer;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) throws Exception {
        Role userRole = roleRepository.findByName(RoleName.USER);
        if (Objects.isNull(userRole)) {
            roleRepository.save(new Role(RoleName.USER));
        }

    }
}
