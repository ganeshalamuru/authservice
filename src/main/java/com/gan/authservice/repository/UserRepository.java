package com.gan.authservice.repository;

import com.gan.authservice.model.security.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface UserRepository extends JpaRepository<User, UUID> {

}
