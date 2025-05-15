package com.gan.authservice.repository;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.enums.RoleName;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Role findByName(RoleName name);
}
