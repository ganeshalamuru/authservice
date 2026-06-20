package com.gan.authservice.service.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CustomDaoAuthenticationProvider extends DaoAuthenticationProvider {

    public CustomDaoAuthenticationProvider(
        @Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {
        super(userDetailsService);
        setPasswordEncoder(passwordEncoder);
    }

}
