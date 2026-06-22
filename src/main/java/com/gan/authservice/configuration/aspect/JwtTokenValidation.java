package com.gan.authservice.configuration.aspect;

import com.gan.authservice.repository.RedisRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Aspect
@RequiredArgsConstructor
public class JwtTokenValidation {

    private final RedisRepository redisRepository;

    @Before("execution(* com.gan.authservice.controller..*.*(..)) && " +
        "(@within(com.gan.authservice.configuration.annotation.JwtValid) || @annotation(com.gan.authservice.configuration.annotation.JwtValid))")
    public void validateJwtToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AuthorizationDeniedException("not authorized");
        }
        String userId = jwtAuthentication.getToken().getSubject();
        String accessToken = jwtAuthentication.getToken().getTokenValue();
        String currentToken = redisRepository.get(userId);
        if (Objects.isNull(currentToken) || !accessToken.equals(currentToken)) {
            throw new AuthorizationDeniedException("not authorized");
        }
    }

}
