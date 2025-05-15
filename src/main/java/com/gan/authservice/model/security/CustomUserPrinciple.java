package com.gan.authservice.model.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@RequiredArgsConstructor
public class CustomUserPrinciple implements UserDetails {

    private final UserCredential userCredential;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(userCredential.getUser().getRole().getName().name()));
    }

    @Override
    public String getPassword() {
        return userCredential.getEncryptedPassword();
    }

    @Override
    public String getUsername() {
        return userCredential.getUsername();
    }

}
