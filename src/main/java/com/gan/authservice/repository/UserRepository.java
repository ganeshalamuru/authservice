package com.gan.authservice.repository;

import com.gan.authservice.model.security.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository  extends JpaRepository<User, Long> {

    User findByUsername(String username);

}
