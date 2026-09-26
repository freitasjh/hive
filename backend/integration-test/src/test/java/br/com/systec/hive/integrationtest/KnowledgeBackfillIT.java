package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeBackfillIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    private void runBackfillSql() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/knowledge/V13__backfill_legacy_kb.sql");
        assertThat(resource.exists()).isTrue();
        String sql;
        try (var in = resource.getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String stmt : sql.split(";")) {
            if (!stmt.isBlank()) {
                jdbcTemplate.execute(stmt);
            }
        }
    }

    @Test
    void when_backfill_with_legacyArrays_then_kbsAndLinks() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenantAndUser(tenantA, "bf-a", PASSWORD);
        seedTenantAndUser(tenantB, "bf-b", PASSWORD);

        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();
        UUID agentB = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO agent (id, tenant_id, name, active, is_default, knowledge_bases) VALUES (?, ?, ?, ?, ?, ARRAY['Guia','FAQ'])",
                agent1, tenantA, "Agent 1", true, false);
        jdbcTemplate.update("INSERT INTO agent (id, tenant_id, name, active, is_default, knowledge_bases) VALUES (?, ?, ?, ?, ?, ARRAY['Guia'])",
                agent2, tenantA, "Agent 2", true, false);
        jdbcTemplate.update("INSERT INTO agent (id, tenant_id, name, active, is_default, knowledge_bases) VALUES (?, ?, ?, ?, ?, ARRAY['Guia'])",
                agentB, tenantB, "Agent B", true, false);

        runBackfillSql();

        Integer kbCountA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_base WHERE tenant_id = ?", Integer.class, tenantA);
        assertThat(kbCountA).isEqualTo(2);
        Integer kbCountB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_base WHERE tenant_id = ?", Integer.class, tenantB);
        assertThat(kbCountB).isEqualTo(1);
        Integer links = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_knowledge_base WHERE tenant_id = ?", Integer.class, tenantA);
        assertThat(links).isEqualTo(3);

        runBackfillSql();

        Integer kbCountA2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_base WHERE tenant_id = ?", Integer.class, tenantA);
        Integer links2 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_knowledge_base WHERE tenant_id = ?", Integer.class, tenantA);
        assertThat(kbCountA2).isEqualTo(2);
        assertThat(links2).isEqualTo(3);
    }
}
