package com.gan.authservice.service.security.dto;

import lombok.Data;

@Data
public class AccessTokenResponse {

    private String accessToken;
    private long expiresAt;

    public AccessTokenResponse(String accessToken, long expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }

}
