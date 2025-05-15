package com.gan.authservice.repository;

import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {

    UserCredential findByUsername(String username);

}
