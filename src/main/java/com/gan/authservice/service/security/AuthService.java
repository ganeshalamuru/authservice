package com.gan.authservice.service.security;

import static com.gan.authservice.constants.JWTConstants.JWT_AUTHORITIES_CLAIM_NAME;
import static com.gan.authservice.constants.JWTConstants.JWT_ISSUER;

import com.gan.authservice.model.Status;
import com.gan.authservice.model.security.CustomUserPrinciple;
import com.gan.authservice.model.security.Role;
import com.gan.authservice.model.security.User;
import com.gan.authservice.model.security.UserCredential;
import com.gan.authservice.model.security.UserToken;
import com.gan.authservice.model.security.enums.RoleName;
import com.gan.authservice.repository.RedisRepository;
import com.gan.authservice.repository.RoleRepository;
import com.gan.authservice.repository.UserCredentialRepository;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.repository.UserTokenRepository;
import com.gan.authservice.service.security.dto.AccessTokenResponse;
import com.gan.authservice.service.security.dto.UserSignupRequest;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserTokenRepository userTokenRepository;
    private final RoleRepository roleRepository;
    private final RedisRepository redisRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder encoder;
    private final JwsHeader jwsHeader;

    @Transactional
    public void createUser(UserSignupRequest userSignupRequest) {
        Role role = roleRepository.findByName(RoleName.USER);
        User user = new User(userSignupRequest.getFirstName(), userSignupRequest.getLastName(), role);
        UserCredential userCredential = new UserCredential(user, userSignupRequest.getUsername(),
            userSignupRequest.getPassword(), passwordEncoder.encode(userSignupRequest.getPassword()));
        userCredential.setUser(user);
        userCredentialRepository.save(userCredential);
    }

    public AccessTokenResponse generateToken(Authentication authentication) {
        Instant now = Instant.now();
        CustomUserPrinciple userPrinciple = (CustomUserPrinciple) authentication.getPrincipal();
        User user = userPrinciple.getUserCredential().getUser();
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
        UserToken userToken = new UserToken(user, accessTokenValue);
        userTokenRepository.save(userToken);
        redisRepository.save(user.getId().toString(), accessTokenValue);
        return new AccessTokenResponse(accessTokenValue, user.getId().toString());
    }

    public void logout(String userId, JwtAuthenticationToken authToken) {
        UserToken userToken = userTokenRepository.findByUserIdAndAccessTokenAndStatus(UUID.fromString(userId), authToken.getToken().getTokenValue(), Status.ACTIVE);
        if (Objects.isNull(userToken)) {
            throw new IllegalStateException("user token in missing");
        }
        userToken.setStatus(Status.INACTIVE);
        userToken.setDeletedAt(LocalDateTime.now());
        redisRepository.delete(userId);
        userTokenRepository.save(userToken);
    }

}
