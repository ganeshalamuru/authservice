package com.gan.authservice.service.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccessTokenResponse {

    private String accessToken;
    private String userId;

}
