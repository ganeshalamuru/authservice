package com.gan.authservice.repository;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.enums.RoleName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);
}
