package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT do seed do prompt generalista (agent V6), em Postgres real (Testcontainers).
 *
 * <p>O seed roda no boot, antes de este teste inserir qualquer tenant. Por isso cada caso
 * reaplica o arquivo de migration pelo JDBC: é a mesma operação que o Flyway executou, e é
 * o que prova a idempotência (ON CONFLICT ... DO NOTHING) e a contagem por tenant.
 */
class PromptSeedIT extends WebIntegrationTest {

    private static final String MIGRATION_RESOURCE = "db/migration/agent/V6__seed_default_prompt.sql";
    private static final String DEFAULT_CODE = "default_general";

    @Test
    void when_migrationApplied_then_eachExistingTenantHasExactlyOneActiveDefaultPrompt() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenantAndUser(tenantA, "seed-default-a-" + UUID.randomUUID(), "it-pw");
        seedTenantAndUser(tenantB, "seed-default-b-" + UUID.randomUUID(), "it-pw");

        applyMigration();

        assertThat(countDefaults(tenantA)).isEqualTo(1);
        assertThat(countDefaults(tenantB)).isEqualTo(1);

        Map<String, Object> row = defaultPrompt(tenantA);
        assertThat(row.get("version")).isEqualTo(1);
        assertThat((String) row.get("name")).isNotBlank();
        assertThat((String) row.get("template")).isNotBlank();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
    }

    @Test
    void when_migrationReExecuted_then_noDuplicatePromptIsCreated() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "seed-default-repeat-" + UUID.randomUUID(), "it-pw");

        applyMigration();
        applyMigration();
        applyMigration();

        // queryForMap já estouraria se houvesse duas linhas; a contagem explícita documenta o
        // limite que o UNIQUE (tenant_id, code, version) e o índice parcial ACTIVE impõem.
        assertThat(countDefaults(tenantId)).isEqualTo(1);
        Integer allRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(allRows).isEqualTo(1);
    }

    @Test
    void when_tenantIsCreatedAfterFirstSeed_then_onlyReRunningMigrationSeedsIt() throws Exception {
        // Limitação documentada: tenant novo depois da migration do boot NÃO recebe prompt
        // automaticamente (sem trigger cross-module). Reexecutar a migration é o que o semeia.
        UUID first = UUID.randomUUID();
        seedTenantAndUser(first, "seed-default-first-" + UUID.randomUUID(), "it-pw");
        applyMigration();

        UUID second = UUID.randomUUID();
        seedTenantAndUser(second, "seed-default-second-" + UUID.randomUUID(), "it-pw");
        assertThat(countDefaults(second)).isZero();

        applyMigration();

        assertThat(countDefaults(first)).isEqualTo(1);
        assertThat(countDefaults(second)).isEqualTo(1);
    }

    @Test
    void when_tenantAlreadyHasActiveV2_then_seedDoesNotInsertOrBreakPartialIndex() throws Exception {
        // A segunda condição do NOT EXISTS (`status = 'ACTIVE'`) existe para não estourar o
        // índice parcial uk_prompt_template_active_version quando já há uma v2 ativa com o
        // mesmo code. Sem ela, o INSERT v1 ACTIVE quebraria a migration inteira.
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "seed-v2-" + UUID.randomUUID(), "it-pw");
        jdbcTemplate.update(
                "INSERT INTO prompt_template (tenant_id, name, template, version, code, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                tenantId, "custom", "x", 2, DEFAULT_CODE);

        applyMigration();

        assertThat(countDefaults(tenantId)).isEqualTo(1);
        assertThat(defaultPrompt(tenantId).get("name")).isEqualTo("custom");
    }

    private void applyMigration() throws IOException {
        try (InputStream in = new ClassPathResource(MIGRATION_RESOURCE).getInputStream()) {
            jdbcTemplate.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private int countDefaults(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId, DEFAULT_CODE);
        return count == null ? 0 : count;
    }

    private Map<String, Object> defaultPrompt(UUID tenantId) {
        return jdbcTemplate.queryForMap(
                "SELECT version, name, template, status FROM prompt_template "
                        + "WHERE tenant_id = ? AND code = ? AND status = ?",
                tenantId, DEFAULT_CODE, "ACTIVE");
    }
}
