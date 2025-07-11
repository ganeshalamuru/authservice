package com.gan.authservice.controller.security;

import static com.gan.authservice.constants.HTTPConstants.USER_ID_HEADER;

import com.gan.authservice.configuration.annotation.JwtValid;
import com.gan.authservice.service.security.AuthService;
import com.gan.authservice.service.security.dto.AccessTokenResponse;
import com.gan.authservice.service.security.dto.UserLoginRequest;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<String> createUser(@Valid @RequestBody UserSignupRequest userSignupRequest) {
        authService.createUser(userSignupRequest);
        return new ResponseEntity<>("Successfully signed up", HttpStatus.CREATED);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(useParameterTypeSchema = true,
        content = @Content(schema = @Schema(implementation = UserLoginRequest.class)))
    public ResponseEntity<AccessTokenResponse> login(Authentication authentication) {
        return new ResponseEntity<>(authService.generateToken(authentication), HttpStatus.OK);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @PostMapping(value = "/logout")
    @JwtValid
    public ResponseEntity<String> logout(@RequestHeader(USER_ID_HEADER) String userId, JwtAuthenticationToken token) {
        authService.logout(userId, token);
        return new ResponseEntity<>("Successfully logged out", HttpStatus.OK);
    }

}
