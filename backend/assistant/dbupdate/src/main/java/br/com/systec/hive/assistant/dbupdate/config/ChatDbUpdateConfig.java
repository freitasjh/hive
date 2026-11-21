package br.com.systec.hive.assistant.dbupdate.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class ChatDbUpdateConfig {

    @Bean(initMethod = "migrate")
    public Flyway agentFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:/db/migration/agent")
                .table("flyway_schema_history_agent")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load();
        // O `repair()` realinha o checksum e a descrição das migrations JÁ APLICADAS com as
        // disponíveis, e marca como `deleted` as versions que sumiram do diretório. Ele NÃO
        // executa SQL: ele conserta o histórico para o código novo "bater" com o que está
        // gravado. Consequência prática: REESCREVER O HISTÓRICO É INVISÍVEL PARA O FLYWAY. Com
        // colisão de número de versão (uma V2 nova contra a V2 antiga no banco), o repair
        // realinha, o migrate roda ZERO migrations e reporta SUCESSO — medido com Flyway
        // 10.10.0. Sem o repair() o mesmo cenário morre alto e verboso
        // ("Migration checksum mismatch for version 2"). Então: para reescrever migrations,
        // DROP SCHEMA do módulo primeiro (ou versionar as novas FORA da faixa já usada). Sem
        // isso, o boot sobe verde com o schema antigo e ninguém percebe.
        flyway.repair();
        return flyway;
    }
}
