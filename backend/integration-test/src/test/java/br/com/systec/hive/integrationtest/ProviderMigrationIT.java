package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderMigrationIT extends WebIntegrationTest {

    private UUID insertLegacyAgent(String llmModel, String providerType, Double temperature,
                                   Double topP, Integer maxTokens) {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO agent (id, tenant_id, name, llm_model, provider_type, temperature, top_p, max_tokens, active, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, "Legacy " + llmModel, llmModel, providerType, temperature, topP, maxTokens, true, false);
        return id;
    }

    private Map<String, Object> loadAgent(UUID id) {
        return jdbcTemplate.queryForMap("SELECT provider_type, llm_model, temperature, top_p, max_tokens FROM agent WHERE id = ?", id);
    }

    private void applyMigration() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/agent/V10__migrate_provider_to_vendor.sql");
        String sql = resource.getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    @Test
    void when_migrate_with_legacySeeds_then_mapsVendorAndModel() throws Exception {
        UUID ollamaId = insertLegacyAgent("OLLHAMA", "LANGCHAIN4J", 0.5, 0.9, 1024);
        UUID openaiId = insertLegacyAgent("OPENAI", "LANGCHAIN4J", 0.7, null, null);
        UUID geminiId = insertLegacyAgent("GEMINI", "SPRING_AI", null, null, 2048);
        UUID compatibleId = insertLegacyAgent("OPENCODE", "LANGCHAIN4J", 0.3, 0.8, 512);

        applyMigration();

        assertThat(loadAgent(ollamaId)).containsEntry("provider_type", "OLLAMA").containsEntry("llm_model", "llama3.2");
        assertThat(loadAgent(openaiId)).containsEntry("provider_type", "OPENAI").containsEntry("llm_model", "gpt-4");
        assertThat(loadAgent(geminiId)).containsEntry("provider_type", "GEMINI").containsEntry("llm_model", "gemini-1.5-pro");
        assertThat(loadAgent(compatibleId)).containsEntry("provider_type", "OPENAI_COMPATIBLE").containsEntry("llm_model", "mimo-v2.5");
    }

    @Test
    void when_migrate_with_rerun_then_noop() throws Exception {
        UUID id = insertLegacyAgent("OPENAI", "LANGCHAIN4J", 0.7, 0.9, 1024);

        applyMigration();
        Map<String, Object> afterFirst = loadAgent(id);
        applyMigration();
        Map<String, Object> afterSecond = loadAgent(id);

        assertThat(afterSecond.get("provider_type")).isEqualTo(afterFirst.get("provider_type"));
        assertThat(afterSecond.get("llm_model")).isEqualTo(afterFirst.get("llm_model"));
        assertThat(String.valueOf(afterSecond.get("llm_model"))).isEqualTo("gpt-4");
    }

    @Test
    void when_migrate_then_preservesInferenceParams() throws Exception {
        UUID id = insertLegacyAgent("OLLHAMA", "LANGCHAIN4J", 0.42, 0.77, 777);

        applyMigration();

        Map<String, Object> row = loadAgent(id);
        assertThat(((Number) row.get("temperature")).doubleValue()).isEqualTo(0.42);
        assertThat(((Number) row.get("top_p")).doubleValue()).isEqualTo(0.77);
        assertThat(((Number) row.get("max_tokens")).intValue()).isEqualTo(777);
    }

    @Test
    void when_migrate_with_nullLlmModel_then_leavesUntouched() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO agent (id, tenant_id, name, llm_model, provider_type, active, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, "Null model", null, "LANGCHAIN4J", true, false);

        applyMigration();

        Map<String, Object> row = loadAgent(id);
        assertThat(row.get("llm_model")).isNull();
        assertThat(row.get("provider_type")).isEqualTo("LANGCHAIN4J");
    }
}
