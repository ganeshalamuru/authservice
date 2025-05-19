package com.gan.authservice.configuration.aspect;

import com.gan.authservice.repository.RedisRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@RequiredArgsConstructor
public class JwtTokenValidation {

    private final RedisRepository redisRepository;

    @Before("execution(* com.gan.authservice.controller..*.*(..)) && " +
        "(@within(com.gan.authservice.configuration.annotation.JwtValid) || @annotation(com.gan.authservice.configuration.annotation.JwtValid))")
    public void validateJwtToken() {
        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        String accessToken = request.getHeader(HttpHeaders.AUTHORIZATION).split(" ")[1];
        String userId = request.getHeader("user-id");
        String currentToken = redisRepository.get(userId);
        if (Objects.isNull(accessToken) || Objects.isNull(currentToken) || !accessToken.equals(currentToken)) {
            throw new AuthorizationDeniedException("not authorized");
        }
    }

}
