package com.gan.authservice;

import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.constants.RegisteredClientProperties;
import com.gan.authservice.constants.SuperAdminProperties;
import com.gan.authservice.demo.OAuth2DemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, RegisteredClientProperties.class, SuperAdminProperties.class,
    OAuth2DemoProperties.class})
public class AuthserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthserviceApplication.class, args);
	}

}
