package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /v1/agents/{id}/knowledge-bases} — o caminho de escrita do vínculo agente↔KB (RN-KB4).
 *
 * <p>O caso central é o anti-IDOR: {@code 404} para o agente de outro tenant é o que impede que
 * alguém vincule as próprias bases ao agente de outra organização — exatamente o que a rota
 * anterior permitia, já que ela não checava de quem era o agente.
 */
class AgentKnowledgeBaseLinkIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Test
    void when_putWithoutToken_then_401() throws Exception {
        UUID agentId = UUID.randomUUID();

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_putWithoutPermission_then_403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "sem-permissao";
        seedTenantAndUser(tenantId, username, PASSWORD);
        // O agente é criado com permissão plena; o token sem AGENT_MANAGE é o mesmo usuário, para que
        // a única diferença na chamada seguinte seja a permissão.
        UUID agentId = createAgent(tenantId, jwtTestHelper.generateToken(username, tenantId), "Agente Alvo");
        String token = jwtTestHelper.generateToken(username, tenantId, java.util.List.of("CHAT_SEND"));

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_putWithAgentOfAnotherTenant_then_404AndNoLink() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "dono");
        seedTenantAndUser(otherTenantId, "outro", PASSWORD);
        UUID foreignAgentId = createAgent(otherTenantId, jwtTestHelper.generateToken("outro", otherTenantId),
                "Agente de Outro Tenant");
        UUID kbId = createKb(tenantId, token, "KB do atacante");
        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", foreignAgentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isNotFound());

        assertThat(linkCount(foreignAgentId, tenantId)).isZero();
        assertThat(linkCount(foreignAgentId, otherTenantId)).isZero();
    }

    @Test
    void when_putWithUnknownAgent_then_404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "desconhecido");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void when_putWithNullKbIds_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kbids-nulo");
        UUID agentId = createAgent(tenantId, token, "Agente KbIds Nulo");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void when_putWithMalformedKbId_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kbids-malformado");
        UUID agentId = createAgent(tenantId, token, "Agente KbId Malformado");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"nao-e-uuid\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_putWithMoreThanFiveKbs_then_400WithPtBrMessage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "limite");
        UUID agentId = createAgent(tenantId, token, "Agente Limite");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[" + kbIdsJson(tenantId, token, 6) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Máximo de 5 bases de conhecimento")));

        assertThat(linkCount(agentId, tenantId)).isZero();
    }

    @Test
    void when_putWithKnowledgeBaseOfAnotherTenant_then_404AndPreviousLinksSurvive() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "misto");
        UUID ownKb = createKb(tenantId, token, "KB propria");
        seedTenantAndUser(otherTenantId, "dono-outro", PASSWORD);
        UUID foreignKb = createKb(otherTenantId, jwtTestHelper.generateToken("dono-outro", otherTenantId),
                "KB alheia");
        UUID agentId = createAgent(tenantId, token, "Agente Misto");

        link(token, agentId, ownKb);
        assertThat(linkCount(agentId, tenantId)).isEqualTo(1);

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + foreignKb + "\"]}"))
                .andExpect(status().isNotFound());

        // A falha é transacional: a base válida continua vinculada em vez de o conjunto virar parcial.
        assertThat(linkCount(agentId, tenantId)).isEqualTo(1);
    }

    @Test
    void when_putTwice_then_secondCallReplacesTheFirstSet() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "substitui");
        UUID kb1 = createKb(tenantId, token, "KB 1");
        UUID kb2 = createKb(tenantId, token, "KB 2");
        UUID agentId = createAgent(tenantId, token, "Agente Substituicao");

        link(token, agentId, kb1);
        assertThat(linkCount(agentId, tenantId)).isEqualTo(1);

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kb2 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kbIds[0]").value(kb2.toString()))
                .andExpect(jsonPath("$.kbNames[0]").value("KB 2"));

        assertThat(linkCount(agentId, tenantId)).isEqualTo(1);
        assertThat(linkedKbIds(agentId, tenantId)).containsExactly(kb2);
    }

    @Test
    void when_putWithEmptyList_then_unlinksEverythingAndReturns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "desvincula");
        UUID kbId = createKb(tenantId, token, "KB removida");
        UUID agentId = createAgent(tenantId, token, "Agente Sem Vinculo");

        link(token, agentId, kbId);
        assertThat(linkCount(agentId, tenantId)).isEqualTo(1);

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(agentId.toString()))
                .andExpect(jsonPath("$.kbIds").isEmpty())
                .andExpect(jsonPath("$.kbNames").isEmpty());

        assertThat(linkCount(agentId, tenantId)).isZero();
    }

    @Test
    void when_agentHasNoLink_then_putWithEmptyListReturns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "sem-vinculo");
        UUID agentId = createAgent(tenantId, token, "Agente Limpo");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kbIds").isEmpty());
    }

    @Test
    void when_putWithFiveKbs_then_200AndAllLinked() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "cinco");
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(createKb(tenantId, token, "KB " + i)).append('"');
        }
        UUID agentId = createAgent(tenantId, token, "Agente Cinco");

        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[" + ids + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kbIds.length()").value(5));

        assertThat(linkCount(agentId, tenantId)).isEqualTo(5);
    }

    @Test
    void when_getLinks_then_returnsTheIdsJustWritten() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "leitura");
        UUID kbId = createKb(tenantId, token, "KB leitura");
        UUID agentId = createAgent(tenantId, token, "Agente Leitura");

        link(token, agentId, kbId);

        mockMvc.perform(get("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(kbId.toString()));
    }

    @Test
    void when_putWithEmptyListTwice_then_bothCallsAre200() throws Exception {
        // Desvincular é idempotente por natureza, mas a segunda chamada percorre de novo a
        // autorização, a validação e a transação. Se algum desses passos não fosse idempotente, o
        // 200 da primeira chamada esconderia a falha.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "desvincula-duas-vezes");
        UUID kbId = createKb(tenantId, token, "KB removida duas vezes");
        UUID agentId = createAgent(tenantId, token, "Agente Desvinculado Duas Vezes");
        link(token, agentId, kbId);

        unlinkAll(token, agentId);
        assertThat(linkCount(agentId, tenantId)).isZero();

        unlinkAll(token, agentId);
        assertThat(linkCount(agentId, tenantId)).isZero();

        mockMvc.perform(get("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ============ PATCH /v1/agents/{id} — a mesma relação, o mesmo limite (RN-KB4) ============

    @Test
    void when_patchWithMoreThanFiveKbs_then_400WithTheSameMessageAsThePutRoute() throws Exception {
        // A rota PATCH é a que o KnowledgeBaseDetail usa para vincular uma KB por vez. Ela grava a
        // mesma relação, então um sexto id tem de ser recusado com a mesma frase do PUT — antes vinha
        // KB_TOO_MANY_LINKS (service) contra VALIDATION_ERROR (validação) na rota equivalente.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "patch-limite");
        UUID agentId = createAgent(tenantId, token, "Agente Patch Limite");

        mockMvc.perform(patch("/v1/agents/{id}", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeBaseIds\":[" + kbIdsJson(tenantId, token, 6) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Máximo de 5 bases de conhecimento por agente")));

        assertThat(linkCount(agentId, tenantId)).isZero();
    }

    @Test
    void when_patchWithFiveKbs_then_200() throws Exception {
        // Limite inclusivo: 5 tem que passar por esta rota também.
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "patch-cinco");
        UUID agentId = createAgent(tenantId, token, "Agente Patch Cinco");

        mockMvc.perform(patch("/v1/agents/{id}", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeBaseIds\":[" + kbIdsJson(tenantId, token, 5) + "]}"))
                .andExpect(status().isOk());

        assertThat(linkCount(agentId, tenantId)).isEqualTo(5);
    }

    @Test
    void when_patchWithoutToken_then_401() throws Exception {
        UUID agentId = UUID.randomUUID();

        mockMvc.perform(patch("/v1/agents/{id}", agentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeBaseIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_patchWithoutPermission_then_403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "patch-sem-permissao";
        seedTenantAndUser(tenantId, username, PASSWORD);
        UUID agentId = createAgent(tenantId, jwtTestHelper.generateToken(username, tenantId), "Agente Patch Alvo");
        String token = jwtTestHelper.generateToken(username, tenantId, java.util.List.of("CHAT_SEND"));

        mockMvc.perform(patch("/v1/agents/{id}", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeBaseIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_patchWithNullIds_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "patch-nulo");
        UUID agentId = createAgent(tenantId, token, "Agente Patch Nulo");

        mockMvc.perform(patch("/v1/agents/{id}", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knowledgeBaseIds\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------ helpers

    private void unlinkAll(String token, UUID agentId) throws Exception {
        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kbIds").isEmpty());
    }

    /** Creates {@code count} knowledge bases and returns their ids as a JSON array body fragment. */
    private String kbIdsJson(UUID tenantId, String token, int count) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(createKb(tenantId, token, "KB " + count + "-" + i)).append('"');
        }
        return ids.toString();
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private UUID createAgent(UUID tenantId, String token, String name) throws Exception {
        // OPENAI/gpt-4o-mini de propósito: este IT exercita o vínculo, não o modelo. Um provider que
        // exige credencial (OPENCODE_GO/OPENAI_COMPATIBLE) transformaria o 201 em 422 CONFIG_MISSING.
        String body = mockMvc.perform(post("/v1/agents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"provider\":\"OPENAI\","
                                + "\"model\":\"gpt-4o-mini\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createKb(UUID tenantId, String token, String name) throws Exception {
        String body = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"embeddingModel\":\"nomic-embed-text\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private void link(String token, UUID agentId, UUID kbId) throws Exception {
        mockMvc.perform(put("/v1/agents/{id}/knowledge-bases", agentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isOk());
    }

    private int linkCount(UUID agentId, UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM agent_knowledge_base WHERE agent_id = ? AND tenant_id = ?",
                Integer.class, agentId, tenantId);
        return count == null ? 0 : count;
    }

    private java.util.List<UUID> linkedKbIds(UUID agentId, UUID tenantId) {
        return jdbcTemplate.queryForList(
                "SELECT kb_id FROM agent_knowledge_base WHERE agent_id = ? AND tenant_id = ?",
                UUID.class, agentId, tenantId);
    }
}
