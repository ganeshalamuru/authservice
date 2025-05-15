package com.gan.authservice.service.security;

import com.gan.authservice.model.security.CustomUserPrinciple;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service("customUserDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserCredential userCredential = userCredentialRepository.findByUsername(username);
        if (userCredential == null) {
            throw new UsernameNotFoundException(username);
        }
        return new CustomUserPrinciple(userCredential);
    }
}