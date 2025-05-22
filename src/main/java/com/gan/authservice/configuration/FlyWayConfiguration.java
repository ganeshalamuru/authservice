package com.gan.authservice.configuration;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlyWayConfiguration {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    @Value("${spring.datasource.url}")
    private String url;
    @Value("${spring.datasource.username}")
    private String username;
    @Value("#{secretProvider.dbPassword}")
    private String password;

    @Bean
    public DataSource getDatasource() {
        return DataSourceBuilder.create()
            .driverClassName(driverClassName)
            .url(url)
            .username(username)
            .password(password)
            .build();
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        return new Flyway(Flyway.configure()
            .baselineOnMigrate(true)
            .dataSource(getDatasource()));
    }

}
