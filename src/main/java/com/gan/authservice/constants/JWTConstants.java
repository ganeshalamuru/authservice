package com.gan.authservice.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JWTConstants {

    public static final String JWT_ISSUER = "self";
    public static final String JWT_AUTHORITIES_CLAIM_NAME = "role";

}
