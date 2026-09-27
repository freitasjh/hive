package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /v1/chat/sessions} com a sessão apontando para um prompt template.
 *
 * <p>Sucede {@code AgentDeleteCascadeIT}, que morreu com a entidade que ele testava. Duas das
 * garantias que ele carregava continuam vivas e é aqui que ficam travadas: o 409 de prompt em uso
 * e a uniformidade 404 (e não 403) para recurso de outro tenant. A exclusão em cascata de agente não
 * sobrevive porque não há mais agente — e o resto daquele arquivo era sobre a sessão, não sobre ele.
 */
class ChatSessionIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    // ============================================================ criação

    @Test
    void when_createSession_withPromptTemplate_then_201AndPersistsTheTemplate() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-owner");
        UUID promptTemplateId = createPromptTemplate(token, "Prompt de boas-vindas");

        String body = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.promptTemplateId").value(promptTemplateId.toString()))
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", sessionId)).isEqualTo(1);
    }

    @Test
    void when_createSession_withoutPromptTemplate_then_400() throws Exception {
        // Não há prompt padrão e não há sessão sem prompt: a coluna é NOT NULL.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-sem-prompt");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Conversa\"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM chat_session")).isZero();
    }

    @Test
    void when_createSession_withUnknownPromptTemplate_then_404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-fantasma");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + UUID.randomUUID() + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_createSession_withPromptTemplateOfAnotherTenant_then_404AndNothingCreated() throws Exception {
        // Anti-IDOR: 404, não 403 — um 403 confirmaria que o registro existe.
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-atacante");
        String foreignToken = tokenFor(otherTenantId, "sess-dono-alheio");
        UUID foreignTemplate = createPromptTemplate(foreignToken, "Prompt alheio");

        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + foreignTemplate + "\",\"title\":\"Conversa\"}"))
                .andExpect(status().isNotFound());

        assertThat(count("SELECT count(*) FROM chat_session")).isZero();
    }

    // ============================================================ prompt em uso (o 409 que sobrevive)

    @Test
    void when_deletePromptTemplateInUseBySession_then_409WithPtBrMessageAndNoSchemaLeak() throws Exception {
        // O 409 NÃO vem só da FK: o serviço checa antes e lança PromptInUseException, e o
        // ON DELETE RESTRICT é a rede de segurança. O que se verifica aqui é que a recusa é
        // compreensível em pt-BR e que nada do schema vaza (OWASP A05).
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-em-uso");
        UUID promptTemplateId = createPromptTemplate(token, "Template Em Uso");
        createSession(token, promptTemplateId, "Conversa que segura o prompt");

        String body = mockMvc.perform(delete("/v1/prompt-templates/{id}", promptTemplateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMPT_IN_USE"))
                .andReturn().getResponse().getContentAsString();

        assertNoSchemaLeak(body);
        assertThat(body).as("corpo da resposta de 409").contains("sessão de chat");
        // Recusar apagar não pode derrubar a sessão nem o template.
        assertThat(count("SELECT count(*) FROM prompt_template WHERE id = ?", promptTemplateId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_session")).isEqualTo(1);
    }

    @Test
    void when_deleteUnusedPromptTemplate_then_204() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-prompt-livre");
        UUID promptTemplateId = createPromptTemplate(token, "Template Sem Sessão");

        mockMvc.perform(delete("/v1/prompt-templates/{id}", promptTemplateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM prompt_template WHERE id = ?", promptTemplateId)).isZero();
    }

    // ============================================================ o teto de paginação é do SERVIDOR

    @Test
    void when_listPromptsWithSizeAtTheCeiling_then_200AndOneAbove_then_400() throws Exception {
        // O `50` do seletor de prompt é o MESMO contrato em três lugares — o controller, o
        // componente e o teste do componente. O teste do componente prendia o componente à cópia
        // dele mesmo: se alguém baixasse o teto aqui para 20 sem tocar no frontend, o browser
        // levaria 400 em TODA montagem do seletor e o teste de frontend ficaria verde.
        //
        // Este IT é o que fecha o circuito: ele lê o número do SERVIDOR, então uma mudança no
        // controller quebra um teste em vez de quebrar a tela.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sess-teto-pagina");

        mockMvc.perform(get("/v1/prompt-templates?page=0&size=50").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/prompt-templates?page=0&size=51").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ a coluna agent_id foi mesmo embora

    @Test
    void when_migrationsApplied_then_agentIsGoneAndSessionPointsToTheTemplate() throws Exception {
        // Trava a decisão de schema deste batch, que é a parte irreversível do trabalho: o nome da
        // coluna antiga não pode reaparecer e o dado antigo não pode ter sumido.
        assertThat(columnExists("chat_session", "agent_id")).isFalse();
        assertThat(columnExists("chat_session", "agent_id_legacy")).isTrue();
        assertThat(columnExists("chat_session", "prompt_template_id")).isTrue();
        assertThat(tableExists("agent")).isFalse();
        assertThat(columnExists("tenant_llm_config", "provider")).isTrue();
    }

    // ------------------------------------------------------------------ helpers

    private void assertNoSchemaLeak(String body) {
        assertThat(body).doesNotContain("chat_session")
                .doesNotContain("chat_message")
                .doesNotContain("prompt_template")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("REFERENCES")
                .doesNotContain("pg_constraint")
                .doesNotContain("key is still referenced")
                .doesNotContain("violates foreign key");
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createPromptTemplate(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"pt-" + UUID.randomUUID()
                                + "\",\"template\":\"Ola {{nome}}\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private void createSession(String token, UUID promptTemplateId, String title) throws Exception {
        mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promptTemplateId\":\"" + promptTemplateId + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated());
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns"
                        + " WHERE table_name = ? AND column_name = ?", Integer.class, table, column);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + table));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
