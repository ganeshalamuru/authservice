package com.gan.authservice.service.security;

import static com.gan.authservice.constants.HTTPConstants.LOGIN_URL;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class UsernamePasswordJwtTokenAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    public UsernamePasswordJwtTokenAuthenticationFilter(
        AuthenticationManager authenticationManager) {
        super(authenticationManager);
        setFilterProcessesUrl(LOGIN_URL);
        setAuthenticationSuccessHandler(new CustomSuccessfulAuthenticationHandler());
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request,
        HttpServletResponse response, FilterChain chain,
        Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        chain.doFilter(request, response);
    }

}
