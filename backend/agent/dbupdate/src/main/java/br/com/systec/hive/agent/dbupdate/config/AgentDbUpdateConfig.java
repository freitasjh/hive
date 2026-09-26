package br.com.systec.hive.agent.dbupdate.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AgentDbUpdateConfig {

    @Bean(initMethod = "migrate")
    public Flyway agentFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/agent")
                .table("flyway_schema_history_agent")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load();
        flyway.repair();
        return flyway;
    }
}
