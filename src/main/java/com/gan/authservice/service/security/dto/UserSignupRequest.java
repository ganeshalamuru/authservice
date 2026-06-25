package com.gan.authservice.service.security.dto;

import jakarta.validation.constraints.NotBlank;

public record UserSignupRequest(
    @NotBlank String username,
    @NotBlank String password,
    @NotBlank String firstName,
    @NotBlank String lastName) {

}
