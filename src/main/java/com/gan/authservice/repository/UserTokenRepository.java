package com.gan.authservice.repository;

import com.gan.authservice.model.Status;
import com.gan.authservice.model.security.UserToken;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

    UserToken findByUserIdAndAccessTokenAndStatus(UUID userId, String accessToken, Status status);
}
