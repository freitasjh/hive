package br.com.systec.hive.publicapi.dbupdate.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class PublicApiDbUpdateConfig {

    @Bean(initMethod = "migrate")
    public Flyway flywayPublicApi(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/publicapi_db")
                .table("flyway_schema_history_publicapi")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
        // Deliberadamente SEM repair(): realinhar checksums das migrations ja aplicadas
        // mascara reescrita de historico e pode fazer o migrate rodar zero migrations com
        // sucesso falso (armadilha documentada em UserDbUpdateConfig). Ausencia e deliberada.
        return flyway;
    }
}
