package com.gan.authservice.configuration;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlyWayConfiguration {

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return new Flyway(Flyway.configure()
            .baselineOnMigrate(true)
            .dataSource(dataSource));
    }

}
