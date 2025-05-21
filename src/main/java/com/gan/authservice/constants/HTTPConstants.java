package com.gan.authservice.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HTTPConstants {

    public static final String LOGIN_URL = "/auth/login";
    public static final String USER_ID_HEADER = "user-id";

}
