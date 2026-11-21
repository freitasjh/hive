package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.assistant.api.service.ChatService;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF-01 / AC-01..03 (Postgres e Redis reais): o prompt default é `AssistantDefault.PROMPT` (código,
 * ADR-020). `system_fallback` é só registro técnico para a FK `chat_session.prompt_template_id`.
 * Ausência do registro NÃO é erro: o backend garante o registro e o gateway aplica o texto default.
 */
@Import(ChatFallbackIT.StubFactoryConfig.class)
class ChatFallbackIT extends WebIntegrationTest {

    private static final String REPLY = "resposta do fallback";
    private static final String FALLBACK_CODE = "system_fallback";

    /** Prompt final enviado ao modelo — prova que o system efetivo é o default em código. */
    static final List<List<ChatMessage>> CAPTURED = new CopyOnWriteArrayList<>();

    @Autowired
    private ChatService chatService;

    @TestConfiguration
    static class StubFactoryConfig {
        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        LlmClientFactory<ChatModel> stubFactory() {
            return new LlmClientFactory<>() {
                @Override
                public ChatModel createClient(br.com.systec.hive.provider.api.AiProvider provider, String model,
                                              Double temperature, Integer maxTokens, Double topP,
                                              String apiKey, String baseUrl) {
                    return new ChatModel() {
                        @Override
                        public dev.langchain4j.model.chat.response.ChatResponse doChat(
                                dev.langchain4j.model.chat.request.ChatRequest chatRequest) {
                            CAPTURED.add(chatRequest.messages());
                            return dev.langchain4j.model.chat.response.ChatResponse.builder()
                                    .aiMessage(AiMessage.from(REPLY))
                                    .build();
                        }
                    };
                }
            };
        }
    }

    @BeforeEach
    void resetCapture() {
        CAPTURED.clear();
    }

    @Test
    void when_chatWithoutPromptTemplateId_then_sessionPointsToSystemFallbackAndAnswers() throws Exception {
        UUID tenantId = seedTenantWithLlm("fallback-ok-");
        jdbcTemplate.update(
                "INSERT INTO prompt_template (tenant_id, name, template, version, code, status, create_at, update_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())",
                tenantId, "Fallback", AssistantTurnGateway.PROMPT, 1, FALLBACK_CODE);
        UUID fallbackId = fallbackId(tenantId);
        UUID userId = userIdForTenant(tenantId);

        ChatRequest request = new ChatRequest();
        request.setMessage("sem prompt escolhido");

        ChatResponse response = TenantContext.runWithTenant(tenantId,
                () -> chatService.chat(request, tenantId, userId));

        assertThat(response.getMessage()).isEqualTo(REPLY);
        assertThat(response.getSessionId()).isNotNull();
        UUID persisted = jdbcTemplate.queryForObject(
                "SELECT prompt_template_id FROM chat_session WHERE id = ?", UUID.class, response.getSessionId());
        assertThat(persisted).isEqualTo(fallbackId);
        Integer templates = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(templates).isEqualTo(1);
        assertEffectiveSystemIsDefaultInCode();
    }

    @Test
    void when_tenantHasNoActiveFallback_then_createsTechnicalRecordAndAnswers() throws Exception {
        UUID tenantId = seedTenantWithLlm("fallback-missing-");
        UUID userId = userIdForTenant(tenantId);
        // Nenhuma linha em prompt_template: V8 não semeou este tenant.

        ChatRequest first = new ChatRequest();
        first.setMessage("sem fallback ativo");
        ChatResponse firstResponse = TenantContext.runWithTenant(tenantId,
                () -> chatService.chat(first, tenantId, userId));

        assertThat(firstResponse.getMessage()).isEqualTo(REPLY);
        UUID fallbackId = fallbackId(tenantId);
        UUID firstPersisted = jdbcTemplate.queryForObject(
                "SELECT prompt_template_id FROM chat_session WHERE id = ?", UUID.class, firstResponse.getSessionId());
        assertThat(firstPersisted).isEqualTo(fallbackId);
        String text = jdbcTemplate.queryForObject(
                "SELECT template FROM prompt_template WHERE id = ?", String.class, fallbackId);
        assertThat(text).isEqualTo(AssistantTurnGateway.PROMPT);
        assertEffectiveSystemIsDefaultInCode();

        // Idempotente: um segundo turno em sessão nova reusa o MESMO registro técnico.
        ChatRequest second = new ChatRequest();
        second.setMessage("segundo turno sem fallback");
        ChatResponse secondResponse = TenantContext.runWithTenant(tenantId,
                () -> chatService.chat(second, tenantId, userId));
        UUID secondPersisted = jdbcTemplate.queryForObject(
                "SELECT prompt_template_id FROM chat_session WHERE id = ?", UUID.class, secondResponse.getSessionId());
        assertThat(secondPersisted).isEqualTo(fallbackId);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId, FALLBACK_CODE);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void when_fallbackExistsOnlyAsDraft_then_reusesAndActivatesIt() throws Exception {
        UUID tenantId = seedTenantWithLlm("fallback-draft-");
        UUID userId = userIdForTenant(tenantId);
        jdbcTemplate.update(
                "INSERT INTO prompt_template (tenant_id, name, template, version, code, status, create_at, update_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'DRAFT', now(), now())",
                tenantId, "Fallback histórico", "texto antigo irrelevante", 1, FALLBACK_CODE);
        UUID draftId = jdbcTemplate.queryForObject(
                "SELECT id FROM prompt_template WHERE tenant_id = ? AND code = ?", UUID.class, tenantId, FALLBACK_CODE);

        ChatRequest request = new ChatRequest();
        request.setMessage("reativar registro tecnico");
        ChatResponse response = TenantContext.runWithTenant(tenantId,
                () -> chatService.chat(request, tenantId, userId));

        UUID persisted = jdbcTemplate.queryForObject(
                "SELECT prompt_template_id FROM chat_session WHERE id = ?", UUID.class, response.getSessionId());
        assertThat(persisted).isEqualTo(draftId);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM prompt_template WHERE id = ?", String.class, draftId);
        assertThat(status).isEqualTo("ACTIVE");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM prompt_template WHERE tenant_id = ? AND code = ?",
                Integer.class, tenantId, FALLBACK_CODE);
        assertThat(count).isEqualTo(1);
        assertEffectiveSystemIsDefaultInCode();
    }

    @Test
    void when_selectedPromptDoesNotExist_then_clientGetsNotFoundException() throws Exception {
        UUID tenantId = seedTenantWithLlm("selected-missing-");
        UUID userId = userIdForTenant(tenantId);
        UUID selected = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        request.setPromptTemplateId(selected);
        request.setMessage("prompt escolhido inexistente");

        assertThatThrownBy(() -> TenantContext.runWithTenant(tenantId,
                () -> chatService.chat(request, tenantId, userId)))
                .isInstanceOf(PromptTemplateNotFoundException.class);

        Integer sessions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_session WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(sessions).isZero();
    }

    private void assertEffectiveSystemIsDefaultInCode() {
        assertThat(CAPTURED).isNotEmpty();
        List<ChatMessage> prompt = CAPTURED.get(CAPTURED.size() - 1);
        assertThat(prompt.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) prompt.get(0)).text()).isEqualTo(AssistantTurnGateway.PROMPT);
    }

    private UUID seedTenantWithLlm(String usernamePrefix) throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = usernamePrefix + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-pw");
        String token = jwtTestHelper.generateToken(username, tenantId);
        seedCredential(token);
        configureTenantLlm(token);
        return tenantId;
    }

    private UUID userIdForTenant(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE tenant_id = ?", UUID.class, tenantId);
    }

    private UUID fallbackId(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM prompt_template WHERE tenant_id = ? AND code = ?", UUID.class, tenantId, FALLBACK_CODE);
    }

    private void seedCredential(String token) throws Exception {
        mockMvc.perform(put("/v1/provider-credentials/OPENCODE_GO")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
    }

    private void configureTenantLlm(String token) throws Exception {
        mockMvc.perform(put("/v1/tenant/llm-config")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"OPENCODE_GO\",\"model\":\"mimo-v2.5\"}"))
                .andExpect(status().isOk());
    }
}
