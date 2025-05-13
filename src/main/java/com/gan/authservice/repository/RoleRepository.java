package com.gan.authservice.repository;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long> {

    Role findByName(RoleName name);
}
