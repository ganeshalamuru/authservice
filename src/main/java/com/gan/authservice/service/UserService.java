package com.gan.authservice.service;

import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.service.dto.UserResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream().map(UserResponse::createResponse).toList();
    }

    /**
     * Soft-deletes the user: stamps {@code deleted_at} on both the {@code app_user} row and its
     * {@code app_user_credential} row so the {@code @SQLRestriction} on those entities hides them from
     * every query. The user then disappears from listings and can no longer authenticate (the
     * credential lookup misses), while the rows remain as audit history. Already-issued access tokens
     * stay valid until expiry — the documented stateless tradeoff.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        UserCredential credential = userCredentialRepository.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        credential.softDelete();
        credential.getUser().softDelete();
        userCredentialRepository.save(credential);
    }

}
