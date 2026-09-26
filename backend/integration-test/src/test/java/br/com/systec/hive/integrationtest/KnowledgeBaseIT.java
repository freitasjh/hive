package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeBaseIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private String createKb(String token, String name) throws Exception {
        String body = "{\"name\":\"" + name + "\"}";
        String response = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("faq"))
                .andReturn().getResponse().getContentAsString();
        return response.split("\"id\":\"")[1].split("\"")[0];
    }

    private String insertAgent(UUID tenantId) {
        UUID agentId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO agent (id, tenant_id, name, active, is_default) VALUES (?, ?, ?, ?, ?)",
                agentId, tenantId, "IT Agent", true, false);
        return agentId.toString();
    }

    @Test
    void when_crud_with_validKb_then_fullCycle() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb1");

        String kbId = createKb(token, "FAQ");

        mockMvc.perform(get("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(kbId));

        mockMvc.perform(get("/v1/knowledge-bases/" + kbId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimension").value(1536));

        mockMvc.perform(put("/v1/knowledge-bases/" + kbId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("updated"));

        mockMvc.perform(delete("/v1/knowledge-bases/" + kbId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void when_create_with_duplicateName_then_conflict() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb2");
        createKb(token, "FAQ");

        String body = mockMvc.perform(post("/v1/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"FAQ\"}"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        // O corpo é conferido, não só o status. Uma chave duplicada não é um registro "referenciado
        // por outros dados": quem conflita é o próprio registro novo, e não existe dependência para
        // remover. Dizer "remova as dependências" aqui mandava o operador procurar um vínculo que
        // não existe — a mensagem de 409 precisa ser acionável para o caso real.
        //
        // Este é o IT que prova que o ramo ALREADY_EXISTS é alcançado de verdade, e não só em
        // teste de unidade: knowledge_base tem UNIQUE (tenant_id, slug) e o serviço wraps o save
        // num try/catch de DataIntegrityViolationException — mas repository.save() não faz flush,
        // então a violação só estourou no commit, fora do try/catch, e chegou crua ao handler.
        assertThat(body)
                .contains("ALREADY_EXISTS")
                .contains("Já existe um registro")
                .doesNotContain("Remova as dependências")
                .doesNotContain("referenciado por outros dados");

        // E nada do schema vaza: nem nome de tabela, nem de constraint, nem sintaxe de DDL.
        assertThat(body)
                .doesNotContain("knowledge_base")
                .doesNotContain("uq_knowledge_base_tenant_slug")
                .doesNotContain("duplicate key value")
                .doesNotContain("FOREIGN KEY")
                .doesNotContain("REFERENCES");
    }


    @Test
    void when_upload_with_txt_then_accepted() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb3");
        String kbId = createKb(token, "FAQ");

        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain",
                "hello knowledge base".getBytes());

        mockMvc.perform(multipart("/v1/knowledge-bases/" + kbId + "/documents:upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists());

        mockMvc.perform(get("/v1/knowledge-bases/" + kbId + "/documents")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("doc.txt"));
    }

    @Test
    void when_upload_with_executable_then_badRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb4");
        String kbId = createKb(token, "FAQ");

        byte[] mz = new byte[]{'M', 'Z', 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe", "application/octet-stream", mz);

        mockMvc.perform(multipart("/v1/knowledge-bases/" + kbId + "/documents:upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void when_upload_with_oversize_then_payloadTooLarge() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb5");
        String kbId = createKb(token, "FAQ");

        byte[] big = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain", big);

        mockMvc.perform(multipart("/v1/knowledge-bases/" + kbId + "/documents:upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void when_delete_with_link_then_conflictAndForce() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb6");
        String kbId = createKb(token, "FAQ");
        String agentId = insertAgent(tenantId);

        mockMvc.perform(put("/v1/agents/" + agentId + "/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[\"" + kbId + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/v1/knowledge-bases/" + kbId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KB_IN_USE"));

        mockMvc.perform(delete("/v1/knowledge-bases/" + kbId + "?force=true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void when_link_with_sixKbs_then_badRequest() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "kb7");
        String agentId = insertAgent(tenantId);
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            UUID kb = UUID.randomUUID();
            jdbcTemplate.update(
                    "INSERT INTO knowledge_base (id, tenant_id, name, slug, embedding_model, dimension, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    kb, tenantId, "KB" + i, "kb-" + i, "text-embedding-3-small", 1536, "ACTIVE");
            if (i > 0) {
                ids.append(',');
            }
            ids.append('"').append(kb).append('"');
        }

        mockMvc.perform(put("/v1/agents/" + agentId + "/knowledge-bases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kbIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
    }
}
