package com.gan.authservice.service.security;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;

import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.model.Status;
import com.gan.authservice.model.security.CustomUserPrinciple;
import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.UserToken;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.repository.UserTokenRepository;
import com.gan.authservice.service.security.dto.AccessTokenResponse;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserTokenRepository userTokenRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder encoder;
    private final JwsHeader jwsHeader;
    private final JwtProperties jwtProperties;

    @Transactional
    public void createUser(UserSignupRequest userSignupRequest) {
        if (userCredentialRepository.existsByUsername(userSignupRequest.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role role = roleRepository.findByName(RoleName.USER);
        User user = new User(userSignupRequest.getFirstName(), userSignupRequest.getLastName(), role);
        UserCredential userCredential = new UserCredential(user, userSignupRequest.getUsername(), passwordEncoder.encode(userSignupRequest.getPassword()));
        userCredential.setUser(user);
        userCredentialRepository.save(userCredential);
    }

    public AccessTokenResponse generateToken(Authentication authentication) {
        Instant now = Instant.now();
        CustomUserPrinciple userPrinciple = (CustomUserPrinciple) authentication.getPrincipal();
        User user = userPrinciple.getUserCredential().getUser();
        Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtl());
        String scope = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(" "));
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.getIssuer())
            .audience(List.of(jwtProperties.getAudience()))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim(JWT_AUTHORITIES_CLAIM_NAME, scope)
            .build();
        JwtEncoderParameters jwtEncoderParameters = JwtEncoderParameters.from(jwsHeader, claims);
        String accessTokenValue = this.encoder.encode(jwtEncoderParameters).getTokenValue();
        UserToken userToken = new UserToken(user, accessTokenValue);
        userTokenRepository.save(userToken);
        return new AccessTokenResponse(accessTokenValue, user.getId().toString());
    }

    public void logout(JwtAuthenticationToken authToken) {
        String userId = authToken.getToken().getSubject();
        UserToken userToken = userTokenRepository.findByUserIdAndAccessTokenAndStatus(UUID.fromString(userId), authToken.getToken().getTokenValue(), Status.ACTIVE);
        if (Objects.isNull(userToken)) {
            throw new IllegalStateException("user token in missing");
        }
        userToken.setStatus(Status.INACTIVE);
        userToken.setDeletedAt(LocalDateTime.now());
        userTokenRepository.save(userToken);
    }

}
