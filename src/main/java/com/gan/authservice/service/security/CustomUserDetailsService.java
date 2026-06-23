package com.gan.authservice.service.security;

import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.repository.UserCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service("customUserDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private final UserCredentialRepository userCredentialRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredential userCredential = userCredentialRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));
        // Return Spring Security's standard User so the Authorization Server can serialize the
        // principal into the JDBC store out of the box. The user UUID is resolved from the username
        // at token-mint time in the token customizer.
        return User.withUsername(userCredential.getUsername())
            .password(userCredential.getEncryptedPassword())
            .authorities(new SimpleGrantedAuthority(userCredential.getUser().getRole().getName().name()))
            .build();
    }
}
