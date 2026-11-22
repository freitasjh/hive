package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.service.ChatService;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-020/K11 — paridade dos DOIS chamadores da porta, contra Postgres/Redis reais.
 *
 * <p>O mesmo histórico e o mesmo template chegam por {@link ChatService} (humano, histórico do JPA)
 * e por {@link PublicChatService} (público, histórico do payload). O {@code ChatModel} stub captura o
 * {@code ChatRequest} FINAL enviado pelo proxy AiService nos dois caminhos; a asserção é sobre as
 * mensagens — não sobre mocks do gateway, que já estão cobertos nos testes unitários.
 */
@Import(ChatCallerParityIT.CapturingFactoryConfig.class)
class ChatCallerParityIT extends WebIntegrationTest {

    private static final String TEMPLATE = "Voce e um assistente.";
    private static final String REPLY = "resposta do stub";
    private static final String FIRST = "pergunta antiga";
    private static final String LAST = "pergunta";
    private static final String PUBLIC_CLIENT_ID = "public-parity-client";

    /** Baseline ADR-002/CZ01 (2026-11-21): tokens e finish reason caracterizados no gateway. */
    private static final int BASELINE_PROMPT_TOKENS = 120;
    private static final int BASELINE_COMPLETION_TOKENS = 340;
    private static final int BASELINE_TOTAL_TOKENS = 460;
    private static final String BASELINE_FINISH_REASON = "STOP";

    static final List<List<ChatMessage>> CAPTURED = new CopyOnWriteArrayList<>();
    static final List<ChatTrace> TRACES = new CopyOnWriteArrayList<>();

    @Autowired
    private ChatService chatService;

    @Autowired
    private PublicChatService publicChatService;

    @TestConfiguration
    static class CapturingFactoryConfig {
        @Bean
        @Primary
        ChatTraceRecorder capturingTraceRecorder() {
            return TRACES::add;
        }

        @Bean
        @Primary
        @SuppressWarnings("unchecked")
        LlmClientFactory<ChatModel> capturingFactory() {
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
                                    .tokenUsage(new TokenUsage(BASELINE_PROMPT_TOKENS,
                                            BASELINE_COMPLETION_TOKENS, BASELINE_TOTAL_TOKENS))
                                    .finishReason(FinishReason.STOP)
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
        TRACES.clear();
    }

    @Test
    void when_bothCallersSendTheSameConversation_then_theFinalChatRequestsAreIdentical() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "parity-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, "it-pw");
        String token = jwtTestHelper.generateToken(username, tenantId);
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
        String code = "parity-" + UUID.randomUUID().toString().substring(0, 8);

        seedCredential(token);
        configureTenantLlm(token);
        UUID promptTemplateId = createPromptTemplate(token, code);

        // ---- caminho humano: TX1 do 1º turno → TX2 → 2º turno carrega o histórico do JPA.
        ChatRequest first = new ChatRequest();
        first.setPromptTemplateId(promptTemplateId);
        first.setMessage(FIRST);
        ChatResponse firstResponse = chatService.chat(first, tenantId, userId);

        ChatRequest second = new ChatRequest();
        second.setSessionId(firstResponse.getSessionId());
        second.setMessage(LAST);
        chatService.chat(second, tenantId, userId);

        List<ChatMessage> humanPrompt = lastCaptured();
        ChatTrace humanTrace = lastTrace();
        // CZ04: o chamador humano não tem turnId/clientId — javadoc ChatTrace:26-27 exige null/null.
        assertThat(humanTrace.turnId()).isNull();
        assertThat(humanTrace.clientId()).isNull();
        assertBaselineTelemetry(humanTrace);

        // ---- caminho público: mesmo histórico vem no payload; mesma ordem de papéis.
        ChatCompletionRequestDTO dto = new ChatCompletionRequestDTO();
        dto.setPrompt_code(code);
        dto.setStream(false);
        List<ChatCompletionRequestDTO.ChatMessageDTO> messages = new ArrayList<>();
        messages.add(message("user", FIRST));
        messages.add(message("assistant", REPLY));
        messages.add(message("user", LAST));
        dto.setMessages(messages);

        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setAttribute("publicapi.pcode", List.of(code));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PUBLIC_CLIENT_ID, null));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(http));
        try {
            TenantContext.runWithTenant(tenantId, () -> {
                publicChatService.complete(dto);
                return null;
            });
        } finally {
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.clearContext();
        }

        List<ChatMessage> publicPrompt = lastCaptured();
        ChatTrace publicTrace = lastTrace();
        // CZ04: turno público carrega o turnId calculado e o clientId autenticado.
        assertThat(publicTrace.turnId()).isNotNull();
        assertThat(publicTrace.clientId()).isEqualTo(PUBLIC_CLIENT_ID);
        assertBaselineTelemetry(publicTrace);

        // Ordem K4 + convergência: as duas listas são byte a byte iguais, sem conversão divergente.
        assertExactBaseline(humanPrompt);
        assertThat(publicPrompt).hasSameSizeAs(humanPrompt);
        for (int i = 0; i < humanPrompt.size(); i++) {
            assertThat(publicPrompt.get(i)).isInstanceOf(humanPrompt.get(i).getClass());
            assertThat(text(publicPrompt.get(i))).isEqualTo(text(humanPrompt.get(i)));
        }
    }

    private static void assertExactBaseline(List<ChatMessage> prompt) {
        assertThat(prompt).hasSize(4);
        assertThat(prompt.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) prompt.get(0)).text()).isEqualTo(TEMPLATE);
        assertThat(prompt.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) prompt.get(1)).singleText()).isEqualTo(FIRST);
        assertThat(prompt.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) prompt.get(2)).text()).isEqualTo(REPLY);
        assertThat(prompt.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) prompt.get(3)).singleText()).isEqualTo(LAST);
    }

    private static List<ChatMessage> lastCaptured() {
        assertThat(CAPTURED).isNotEmpty();
        return CAPTURED.get(CAPTURED.size() - 1);
    }

    private static ChatTrace lastTrace() {
        assertThat(TRACES).isNotEmpty();
        return TRACES.get(TRACES.size() - 1);
    }

    /** CZ01: os números vêm do ChatResponse do stub e chegam idênticos nos dois chamadores. */
    private static void assertBaselineTelemetry(ChatTrace trace) {
        assertThat(trace.promptTokens()).isEqualTo(BASELINE_PROMPT_TOKENS);
        assertThat(trace.completionTokens()).isEqualTo(BASELINE_COMPLETION_TOKENS);
        assertThat(trace.totalTokens()).isEqualTo(BASELINE_TOTAL_TOKENS);
        assertThat(trace.finishReason()).isEqualTo(BASELINE_FINISH_REASON);
    }

    private static String text(ChatMessage message) {
        if (message instanceof SystemMessage system) {
            return system.text();
        }
        if (message instanceof AiMessage ai) {
            return ai.text();
        }
        if (message instanceof UserMessage user) {
            return user.singleText();
        }
        return null;
    }

    private static ChatCompletionRequestDTO.ChatMessageDTO message(String role, String content) {
        ChatCompletionRequestDTO.ChatMessageDTO dto = new ChatCompletionRequestDTO.ChatMessageDTO();
        dto.setRole(role);
        dto.setContent(content);
        return dto;
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

    private UUID createPromptTemplate(String token, String code) throws Exception {
        String body = mockMvc.perform(post("/v1/prompt-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Paridade\",\"code\":\"" + code + "\",\"template\":\""
                                + TEMPLATE + "\",\"inferenceParams\":{\"temperature\":0.7,\"maxTokens\":256}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        // create() defaulta DRAFT; o resolvedor público exige ACTIVE.
        jdbcTemplate.update("UPDATE prompt_template SET status = 'ACTIVE' WHERE id = ?", id);
        return id;
    }
}
