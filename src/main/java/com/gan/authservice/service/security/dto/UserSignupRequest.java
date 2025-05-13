package com.gan.authservice.service.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserSignupRequest {

    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;


}
