package com.gan.authservice.initializer;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class RoleInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        for (RoleName roleName : RoleName.values()) {
            if (Objects.isNull(roleRepository.findByName(roleName))) {
                roleRepository.save(new Role(roleName));
            }
        }
    }
}
