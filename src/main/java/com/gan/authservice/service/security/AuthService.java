package com.gan.authservice.service.security;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;
import static com.gan.authservice.constants.JWTConstants.JWT_ISSUER;

import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.service.security.dto.AccessTokenResponse;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder encoder;
    private final JwsHeader jwsHeader;

    public void createUser(UserSignupRequest userSignupRequest) {
        Role role = roleRepository.findByName(RoleName.USER);
        User user = new User(userSignupRequest.getUsername(),
            passwordEncoder.encode(userSignupRequest.getPassword()), role);
        user.setFirstName(userSignupRequest.getFirstName());
        user.setLastName(userSignupRequest.getLastName());
        user.setMetaData();
        userRepository.save(user);
    }

    public AccessTokenResponse generateToken(Authentication authentication) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(Duration.ofHours(1).toSeconds());
        String scope = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(" "));
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(JWT_ISSUER)
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(authentication.getName())
            .claim(JWT_AUTHORITIES_CLAIM_NAME, scope)
            .build();
        JwtEncoderParameters jwtEncoderParameters = JwtEncoderParameters.from(jwsHeader, claims);
        String accessTokenValue = this.encoder.encode(jwtEncoderParameters).getTokenValue();
        return new AccessTokenResponse(accessTokenValue, expiresAt.getEpochSecond());
    }

}
