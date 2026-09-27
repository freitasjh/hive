package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code DELETE /v1/agents/{id}} com histórico de chat.
 *
 * <p>Bug P0 de 2026-09-26: {@code chat_session.agent_id} referenciava {@code agent(id)} sem cláusula
 * {@code ON DELETE} (isto é, {@code NO ACTION}), então um agente com uma única sessão respondia 409
 * e nenhum agente do tenant podia ser excluído. A V14 do módulo {@code agent} traz o CASCADE.
 *
 * <p>O CASCADE é destrutivo e irreversível: excluir o agente apaga as conversas e as mensagens sem
 * lixeira. Estes testes existem para travar esse contrato — se alguém remover o CASCADE, o 204
 * volta a ser 409 e o primeiro teste falha.
 */
class AgentDeleteCascadeIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    // ============================================================ 204 + remoção real em cascata

    @Test
    void when_deleteAgentWithSessionAndMessages_then_204AndEverythingCascades() throws Exception {
        // O critério de aceite do bug: o caminho que hoje dá 409 tem de dar 204 e o histórico tem de
        // sumir de verdade — não basta o 204, o dado tem de estar gone.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-cascade");

        UUID alvo = createAgent(tenantId, token, "Agente com Conversa");
        UUID outro = createAgent(tenantId, token, "Agente Vizinho");
        UUID sessionId = createSession(token, alvo, "Conversa que vai sumir");
        insertMessage(tenantId, sessionId, "USER", "pergunta que vai sumir");
        insertMessage(tenantId, sessionId, "ASSISTANT", "resposta que vai sumir");

        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", sessionId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_message WHERE session_id = ?", sessionId)).isEqualTo(2);

        mockMvc.perform(delete("/v1/agents/{id}", alvo).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", alvo)).isZero();
        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", sessionId)).isZero();
        assertThat(count("SELECT count(*) FROM chat_message WHERE session_id = ?", sessionId)).isZero();

        // E o cascade não encosta no agente vizinho, que não tem conversa.
        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", outro)).isEqualTo(1);
    }

    @Test
    void when_deleteAgentWithTwoSessions_then_bothCascade() throws Exception {
        // Uma sessão já era suficiente para travar o agente; com duas, a limpeza não pode parar na
        // primeira e deixar a segunda órfã.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-duas-sessoes");

        UUID alvo = createAgent(tenantId, token, "Agente Duas Conversas");
        UUID s1 = createSession(token, alvo, "Primeira");
        UUID s2 = createSession(token, alvo, "Segunda");
        insertMessage(tenantId, s1, "USER", "m1");

        mockMvc.perform(delete("/v1/agents/{id}", alvo).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM chat_session WHERE agent_id = ?", alvo)).isZero();
        assertThat(count("SELECT count(*) FROM chat_message WHERE session_id = ?", s1)).isZero();
        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", s2)).isZero();
    }

    // ============================================================ não regredir

    @Test
    void when_deleteAgentWithoutSession_then_204() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-limpo");
        UUID agentId = createAgent(tenantId, token, "Agente Sem Conversa");

        mockMvc.perform(delete("/v1/agents/{id}", agentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", agentId)).isZero();
    }

    @Test
    void when_deleteAgentWithoutToken_then_401AndAgentSurvives() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-sem-token");
        UUID agentId = createAgent(tenantId, token, "Agente Protegido");
        createSession(token, agentId, "Conversa protegida");

        mockMvc.perform(delete("/v1/agents/{id}", agentId))
                .andExpect(status().isUnauthorized());

        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", agentId)).isEqualTo(1);
    }

    @Test
    void when_deleteAgentWithoutPermission_then_403AndAgentSurvives() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "sem-permissao-delete";
        seedTenantAndUser(tenantId, username, PASSWORD);
        UUID agentId = createAgent(tenantId, jwtTestHelper.generateToken(username, tenantId), "Agente Alvo");
        String token = jwtTestHelper.generateToken(username, tenantId, java.util.List.of("CHAT_SEND"));

        mockMvc.perform(delete("/v1/agents/{id}", agentId).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", agentId)).isEqualTo(1);
    }

    // ============================================================ anti-IDOR

    @Test
    void when_deleteAgentOfAnotherTenant_then_404AndNothingIsCascaded() throws Exception {
        // O 409 que motivou o bug vinha de uma FK global. O risco agora é o oposto: cascade demais.
        // Um tenant não pode derrubar as conversas de outro apagando o agente alheio — e a resposta
        // tem de ser 404 (anti-IDOR uniforme), não 403, que confirmaria a existência do registro.
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "atacante");
        seedTenantAndUser(otherTenantId, "dono-alheio", PASSWORD);

        String foreignToken = jwtTestHelper.generateToken("dono-alheio", otherTenantId);
        UUID foreignAgent = createAgent(otherTenantId, foreignToken, "Agente Alheio");
        UUID foreignSession = createSession(foreignToken, foreignAgent, "Conversa alheia");
        insertMessage(otherTenantId, foreignSession, "USER", "segredo");

        mockMvc.perform(delete("/v1/agents/{id}", foreignAgent).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", foreignAgent)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_session WHERE id = ?", foreignSession)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM chat_message WHERE session_id = ?", foreignSession)).isEqualTo(1);
    }

    @Test
    void when_deleteUnknownAgent_then_404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-desconhecido");

        mockMvc.perform(delete("/v1/agents/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ============================================================ 409 sem vazamento de schema

    @Test
    void when_deletePromptTemplateInUse_then_409WithPtBrMessageAndNoSchemaLeak() throws Exception {
        // O outro caminho de 409 que sobrevive à correção. Ele NÃO vem da FK: o serviço checa antes
        // (`existsByPromptTemplate_IdAndTenantId`) e lança PromptInUseException, e o
        // ON DELETE RESTRICT da V8 é só a rede de segurança. O que se verifica aqui é que a recusa é
        // compreensível em pt-BR e que nada do schema vaza — nem na exceção de domínio, nem se ela
        // deixar a exceção passar e o banco recusar (OWASP A05).
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-prompt");
        UUID promptId = createPrompt(token, "Template Em Uso", "template-em-uso", "Olá {{nome}}");
        UUID agentId = createAgentWithPrompt(token, "Agente Com Prompt", promptId);

        String body = mockMvc.perform(delete("/v1/prompt-templates/{id}", promptId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROMPT_IN_USE"))
                .andReturn().getResponse().getContentAsString();

        assertNoSchemaLeak(body);
        assertThat(body).as("corpo da resposta de 409").contains("em uso por agente");
        // O agente que consome o template continua intacto: recusar apagar o template não pode
        // derrubar o agente.
        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", agentId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM prompt_template WHERE id = ?", promptId)).isEqualTo(1);
    }

    @Test
    void when_deleteAgentSoleDefault_then_409WithActionablePtBrMessageAndNoSchemaLeak() throws Exception {
        // O único agente padrão do tenant não pode ser removido: o chat cairia sem agente default.
        // A mensagem precisa dizer o que fazer, não apenas que falhou.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono-unico-padrao");
        UUID agentId = createAgent(token, "Agente Unico Padrao", true);
        createSession(token, agentId, "Conversa do unico");

        String body = mockMvc.perform(delete("/v1/agents/{id}", agentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertNoSchemaLeak(body);
        assertThat(body).contains("Defina outro agente como padrão");
        assertThat(count("SELECT count(*) FROM agent WHERE id = ?", agentId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Falha se o corpo da resposta expuser qualquer pedaço do schema do banco. Cobre os dois eixos do
     * A05: identificadores (nomes de tabela/constraint) e estrutura (definição da FK).
     *
     * <p>A palavra "agente" em português não entra na lista: o nome da entidade é vocabulário de
     * domínio, não vazamento. O que não pode aparecer é o identificador da tabela, {@code agent}.
     */
    private void assertNoSchemaLeak(String body) {
        assertThat(body).doesNotContain("chat_session");
        assertThat(body).doesNotContain("chat_message");
        assertThat(body).doesNotContain("prompt_template");
        assertThat(body).doesNotContain("FOREIGN KEY");
        assertThat(body).doesNotContain("REFERENCES");
        assertThat(body).doesNotContain("pg_constraint");
        assertThat(body).doesNotContain("key is still referenced");
        assertThat(body).doesNotContain("violates foreign key");
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createAgent(UUID tenantId, String token, String name) throws Exception {
        return createAgent(token, name, false);
    }

    private UUID createAgent(String token, String name, boolean isDefault) throws Exception {
        // OPENAI/gpt-4o-mini de propósito: este IT exercita a exclusão, não o modelo. Um provider que
        // exige credencial (OPENCODE_GO/OPENAI_COMPATIBLE) transformaria o 201 em 422 CONFIG_MISSING.
        String body = mockMvc.perform(post("/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"provider\":\"OPENAI\","
                                + "\"model\":\"gpt-4o-mini\",\"active\":true,\"isDefault\":" + isDefault + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createAgentWithPrompt(String token, String name, UUID promptId) throws Exception {
        String body = mockMvc.perform(post("/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"provider\":\"OPENAI\",\"model\":\"gpt-4o-mini\","
                                + "\"active\":true,\"promptTemplateId\":\"" + promptId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createSession(String token, UUID agentId, String title) throws Exception {
        String body = mockMvc.perform(post("/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"" + agentId + "\",\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createPrompt(String token, String name, String code, String template) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"template\":\"" + template + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    /**
     * Insere a mensagem direto por SQL em vez de chamar {@code POST /v1/chat}: o chat real exige um
     * provider de LLM com credencial, e o que este IT precisa verificar é se a linha some no
     * cascade — não se o modelo responde.
     */
    private void insertMessage(UUID tenantId, UUID sessionId, String role, String content) {
        jdbcTemplate.update(
                "INSERT INTO chat_message (id, tenant_id, session_id, role, content) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, sessionId, role, content);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
