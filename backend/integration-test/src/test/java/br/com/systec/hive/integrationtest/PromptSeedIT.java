package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.impl.ai.AssistantDefault;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-020 RF-04 (Postgres real, Testcontainers): o seed V6 foi desfeito pela V8 e o texto padrão
 * agora mora em {@link AssistantDefault#PROMPT}. V6 permanece imutável no histórico Flyway — este
 * teste a reaplica pelo JDBC para provar que a V8 remove exatamente o que a V6 inseriu, sem tocar
 * templates que o usuário escolheu.
 */
class PromptSeedIT extends WebIntegrationTest {

    private static final String V6 = "db/migration/agent/V6__seed_default_prompt.sql";
    private static final String V8 = "db/migration/agent/V8__remove_default_prompt_seed.sql";
    private static final String DEFAULT_CODE = "default_general";
    private static final String FALLBACK_CODE = "system_fallback";

    /** Texto exato de V6:65-67 — a constante em código não pode divergir do seed histórico. */
    private static final String SEEDED_TEXT = "Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, "
            + "de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe "
            + "em vez de inventar. Mantenha o tom profissional e cordial.";

    @Test
    void when_bootMigrationsRun_then_defaultSeedIsAlreadyGone() {
        // V6 roda no boot e V8 desfaz o dado na sequência: nenhum tenant deve ter default_general.
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE code = ?", Integer.class, DEFAULT_CODE);
        assertThat(count).isZero();
    }

    @Test
    void when_seedIsReapplied_then_v8RemovesIt() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        seedTenantAndUser(tenantA, "seed-default-a-" + UUID.randomUUID(), "it-pw");
        seedTenantAndUser(tenantB, "seed-default-b-" + UUID.randomUUID(), "it-pw");

        apply(V6);
        assertThat(countDefaults(tenantA)).isEqualTo(1);
        assertThat(countDefaults(tenantB)).isEqualTo(1);

        apply(V8);

        assertThat(countDefaults(tenantA)).isZero();
        assertThat(countDefaults(tenantB)).isZero();
    }

    @Test
    void when_v8IsReApplied_then_itIsIdempotent() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "seed-default-repeat-" + UUID.randomUUID(), "it-pw");
        apply(V6);

        apply(V8);
        apply(V8);

        assertThat(countDefaults(tenantId)).isZero();
    }

    @Test
    void when_tenantHasItsOwnPrompt_then_v8KeepsIt() throws Exception {
        UUID tenantId = UUID.randomUUID();
        seedTenantAndUser(tenantId, "seed-v2-" + UUID.randomUUID(), "it-pw");
        jdbcTemplate.update(
                "INSERT INTO prompt_template (tenant_id, name, template, version, code, status) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')",
                tenantId, "custom", "prompt escolhido pelo usuario", 2, "custom_code");
        apply(V6);

        apply(V8);

        Integer custom = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId, "custom_code");
        assertThat(custom).isEqualTo(1);
        assertThat(countDefaults(tenantId)).isZero();
    }

    @Test
    void when_sessionPointsToDefault_then_v8ReallocatesItToSystemFallback() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "seed-session-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-pw");
        UUID userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE username = ?", UUID.class, username);

        apply(V6);
        UUID defaultId = jdbcTemplate.queryForObject(
                "SELECT id FROM prompt_template WHERE tenant_id = ? AND code = ?",
                UUID.class, tenantId, DEFAULT_CODE);

        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO chat_session (id, create_at, update_at, tenant_id, user_id, title, prompt_template_id) "
                        + "VALUES (?, now(), now(), ?, ?, ?, ?)",
                sessionId, tenantId, userId, "sessao legada", defaultId);

        apply(V8);

        UUID reallocatedId = jdbcTemplate.queryForObject(
                "SELECT prompt_template_id FROM chat_session WHERE id = ?", UUID.class, sessionId);
        assertThat(reallocatedId).isNotNull().isNotEqualTo(defaultId);

        String reallocatedCode = jdbcTemplate.queryForObject(
                "SELECT code FROM prompt_template WHERE id = ?", String.class, reallocatedId);
        assertThat(reallocatedCode).isEqualTo(FALLBACK_CODE);

        String reallocatedTemplate = jdbcTemplate.queryForObject(
                "SELECT template FROM prompt_template WHERE id = ?", String.class, reallocatedId);
        assertThat(reallocatedTemplate).isEqualTo(AssistantDefault.PROMPT);

        Integer fallbackCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template "
                        + "WHERE tenant_id = ? AND code = ? AND status = 'ACTIVE' AND version = 1",
                Integer.class, tenantId, FALLBACK_CODE);
        assertThat(fallbackCount).isEqualTo(1);
        assertThat(countDefaults(tenantId)).isZero();
    }

    @Test
    void when_fallbackIsNeeded_then_codeConstantCarriesTheOldSeedText() {
        // R-seed: tenants que tinham o prompt semeado passam a usar AssistantDefault.PROMPT.
        assertThat(AssistantDefault.PROMPT).isEqualTo(SEEDED_TEXT);
    }

    private void apply(String resource) throws IOException {
        try (InputStream in = new ClassPathResource(resource).getInputStream()) {
            jdbcTemplate.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private int countDefaults(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId, DEFAULT_CODE);
        return count == null ? 0 : count;
    }
}
