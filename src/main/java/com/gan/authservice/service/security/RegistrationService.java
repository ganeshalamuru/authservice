package com.gan.authservice.service.security;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserCredentialRepository userCredentialRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void createUser(UserSignupRequest userSignupRequest) {
        if (userCredentialRepository.existsByUsername(userSignupRequest.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role role = roleRepository.findByName(RoleName.USER)
            .orElseThrow(() -> new IllegalStateException("USER role not seeded; RoleInitializer must run first"));
        User user = new User(userSignupRequest.firstName(), userSignupRequest.lastName(), role);
        UserCredential userCredential = new UserCredential(user, userSignupRequest.username(), passwordEncoder.encode(userSignupRequest.password()));
        userCredential.setUser(user);
        userCredentialRepository.save(userCredential);
    }

}
