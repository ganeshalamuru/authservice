package com.gan.authservice.repository;

import com.gan.authservice.model.security.UserCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    Optional<UserCredential> findByUsername(String username);

    boolean existsByUsername(String username);

}
