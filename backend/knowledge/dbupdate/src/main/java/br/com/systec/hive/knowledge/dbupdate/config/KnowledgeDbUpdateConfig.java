package br.com.systec.hive.knowledge.dbupdate.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

@Configuration
public class KnowledgeDbUpdateConfig {

    /**
     * Migra depois do módulo {@code agent}, e isso é uma exigência, não uma preferência.
     *
     * <p>{@code agent_knowledge_base} é tabela do módulo {@code knowledge}, mas a V13 (backfill de
     * {@code Agent.knowledgeBases}) já lia {@code FROM agent}, e a V14 adiciona a foreign key
     * {@code agent_id -> agent(id)}. Sem esta dependência declarada, a ordem entre os dois beans
     * {@code Flyway} fica por conta da ordem de registro do component scan — indeterminada na
     * prática, e a migration pode rodar antes de {@code agent} existir e derrubar o startup.
     *
     * <p>{@code @DependsOn} garante que o bean {@code agentFlyway} esteja criado — e com ele a
     * migration executada, porque {@code initMethod = "migrate"} roda durante a inicialização do
     * bean — antes deste começar.
     */
    @Bean(initMethod = "migrate")
    @DependsOn("agentFlyway")
    public Flyway knowledgeFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/knowledge")
                .table("flyway_schema_history_knowledge")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load();
        flyway.repair();
        return flyway;
    }
}
