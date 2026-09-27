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
        // Mesmo `repair()` e mesma armadilha de `AgentDbUpdateConfig`: realinha o checksum das
        // migrations já aplicadas com as disponíveis e marca como `deleted` as versions que
        // sumiram — SEM executar SQL. Reescrever o histórico deste módulo, portanto, é invisível
        // para o Flyway: com colisão de número de versão o repair realinha, o migrate roda zero
        // migrations e reporta sucesso. Para reescrever, DROP SCHEMA do módulo primeiro (ou
        // versionar as novas fora da faixa já usada). Ver `.agents/rules/db-migration-flyway.md`.
        flyway.repair();
        return flyway;
    }
}
