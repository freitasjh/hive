package br.com.systec.hive.identity.dbupdate.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class UserDbUpdateConfig {

    @Primary
    @Bean(initMethod = "migrate")
    public Flyway flywayIdentity(DataSource dataSource) {
        Flyway flyway =  Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/identity_db")
                .table("flyway_schema_history_identity")
                .baselineOnMigrate(true)
                .load();
        flyway.repair();
        return flyway;
    }
}
