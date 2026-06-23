package com.gan.authservice.controller.security;

import com.gan.authservice.service.security.RegistrationService;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;

    @PostMapping("/signup")
    public ResponseEntity<String> createUser(@Valid @RequestBody UserSignupRequest userSignupRequest) {
        registrationService.createUser(userSignupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully signed up");
    }

}
