package com.gan.authservice.configuration;

import com.gan.authservice.configuration.aspect.JwtTokenValidation;
import com.gan.authservice.repository.RedisRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class ApplicationConfiguration {

    @Bean
    public JwtTokenValidation jwtTokenValidation(RedisRepository redisRepository) {
        return new JwtTokenValidation(redisRepository);
    }

}
