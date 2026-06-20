package com.gan.authservice.configuration;

import com.gan.authservice.constants.DatabaseProperties;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RequiredArgsConstructor
@Configuration
public class FlyWayConfiguration {

    private final DatabaseProperties databaseProperties;

    @Value("#{secretProvider.dbPassword}")
    private String password;

    @Bean
    public DataSource getDatasource() {
        return DataSourceBuilder.create()
            .driverClassName(databaseProperties.getDriverClassName())
            .url(databaseProperties.getUrl())
            .username(databaseProperties.getUsername())
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
