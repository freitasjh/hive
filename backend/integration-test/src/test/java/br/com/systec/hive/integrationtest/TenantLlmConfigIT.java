package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /v1/tenant/llm-config}: o provider e o modelo que respondem por todas as conversas do tenant.
 *
 * <p>Temos uma linha por tenant e nenhuma. O caso que este IT existe para travar é o segundo: um
 * tenant sem configuração tem de receber um erro que diga o que configurar, e não uma conversa
 * respondida por um default implícito. Um default silencioso seria indistinguível, para o operador,
 * de "está tudo bem e o modelo é este".
 */
class TenantLlmConfigIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    // ============================================================ read

    @Test
    void when_get_withoutConfiguration_then_returnsOkAndNull() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-sem-config");

        // 200 e corpo vazio, e não 404: "não configurado" é um estado normal da tela de
        // configuração, e um 404 faria a UI tratar a primeira visita como erro.
        mockMvc.perform(get("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(count("tenant_llm_config")).isZero();
    }

    @Test
    void when_get_withConfiguration_then_returnsIt() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-com-config");

        configure(token, "OPENAI", "gpt-4o-mini");

        mockMvc.perform(get("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("OPENAI"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    // ============================================================ write

    @Test
    void when_put_then_createsTheSingleRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-put");

        configure(token, "OPENCODE_GO", "mimo-v2.5");

        assertThat(count("tenant_llm_config")).isEqualTo(1);
    }

    @Test
    void when_putTwice_then_replacesInsteadOfCreatingASecondRow() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-put-duas");

        configure(token, "OPENAI", "gpt-4o-mini");
        configure(token, "OPENCODE_ZEN", "claude-sonnet-4-5");

        // UNIQUE(tenant_id) é a razão de o segundo PUT não criar uma segunda linha; o teste é o que
        // impede alguém de trocar o upsert por um insert "mais simples".
        assertThat(count("tenant_llm_config")).isEqualTo(1);
        mockMvc.perform(get("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.provider").value("OPENCODE_ZEN"))
                .andExpect(jsonPath("$.model").value("claude-sonnet-4-5"));
    }

    @Test
    void when_put_withBlankModel_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-model-vazio");

        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("tenant_llm_config")).isZero();
    }

    @Test
    void when_put_withNullProvider_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-sem-provider");

        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":null,\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("tenant_llm_config")).isZero();
    }

    @Test
    void when_put_withUnknownProvider_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-provider-fantasma");

        // O enum fecha a lista antes da allowlist: um provider novo entra por decisão explícita em
        // TenantLlmConfigProviders E no CHECK do banco, nunca por omissão.
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"SKYNET\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("tenant_llm_config")).isZero();
    }

    // ============================================================ auth e isolamento

    @Test
    void when_put_withoutToken_then_401() throws Exception {
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(count("tenant_llm_config")).isZero();
    }

    @Test
    void when_put_withoutPermission_then_403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "llm-sem-permissao";
        seedTenantAndUser(tenantId, username, PASSWORD);
        String limited = jwtTestHelper.generateToken(username, tenantId, java.util.List.of("CHAT_SEND"));

        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + limited)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\"}"))
                .andExpect(status().isForbidden());

        assertThat(count("tenant_llm_config")).isZero();
    }

    @Test
    void when_oneOfTwoTenantsConfigures_then_theOtherStillHasNoConfiguration() throws Exception {
        // O tenant vem do principal autenticado, nunca do path nem do body: não existe payload capaz
        // de endereçar a configuração de outro tenant.
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "llm-tenant-a");
        String tokenB = tokenFor(tenantB, "llm-tenant-b");

        configure(tokenA, "OPENAI", "gpt-4o-mini");

        mockMvc.perform(get("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").doesNotExist());

        assertThat(count("tenant_llm_config")).isEqualTo(1);
    }

    @Test
    void when_chatWithoutConfiguration_then_422TellingWhatToConfigure() throws Exception {
        // O comportamento que o requisito pede: sem config o chat falha com erro claro e acionável.
        // Sem fallback global e sem criação automática.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "llm-chat-sem-config");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt sem modelo");

        String body = mockMvc.perform(post("/v1/chat")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"message\":\"oi\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TENANT_LLM_CONFIG_NOT_FOUND"))
                // O detalhe diz a tela que corrige: "Provedor e modelo" (/settings/llm), não a de
                // credenciais. A substring antiga fixava a mensagem errada, não o comportamento.
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Configurações > Provedor e modelo")))
                .andReturn().getResponse().getContentAsString();

        assertNoSchemaLeak(body);
        assertThat(count("chat_message")).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private void assertNoSchemaLeak(String body) {
        assertThat(body).doesNotContain("tenant_llm_config").doesNotContain("prompt_template")
                .doesNotContain("FOREIGN KEY").doesNotContain("REFERENCES").doesNotContain("pg_constraint");
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void configure(String token, String provider, String model) throws Exception {
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"" + provider + "\",\"model\":\"" + model + "\"}"))
                .andExpect(status().isOk());
    }

    private UUID createPromptTemplate(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"pt-" + UUID.randomUUID()
                                + "\",\"template\":\"Voce e um assistente.\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
