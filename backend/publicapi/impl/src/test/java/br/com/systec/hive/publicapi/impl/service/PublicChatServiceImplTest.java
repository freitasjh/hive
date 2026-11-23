package br.com.systec.hive.publicapi.impl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.assistant.api.service.TurnOutcomeReporter;
import br.com.systec.hive.assistant.api.service.TurnPolicyResolver;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionResponse;
import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotActiveException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.PromptVersionNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.SystemRoleNotAllowedException;
import br.com.systec.hive.publicapi.api.exceptions.UnknownModelException;
import br.com.systec.hive.publicapi.impl.quota.QuotaDecision;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicChatServiceImplTest {

    @Mock private PromptTemplateService promptTemplateService;
    @Mock private PromptGrantResolver promptGrantResolver;
    // providerResolution is provided by a subclass override below (avoids mocking final-ish classes on JDK 25)
    @Mock private br.com.systec.hive.ai.api.port.ChatTurnGateway turnGateway;
    @Mock private TurnOutcomeReporter turnOutcomeMapper;
    @Mock private RedisQuotaGuard quotaGuard;

    private PublicChatServiceImpl svc() {
        TurnPolicyResolver providerResolution = new TurnPolicyResolver() {
            @Override
            public Resolved resolve(java.util.UUID tenantId) {
                return new Resolved(br.com.systec.hive.provider.api.AiProvider.OPENAI, "gpt-4", "key", "url");
            }

            @Override
            public Resolved resolve(java.util.UUID tenantId, AiProvider providerOverride, String modelOverride) {
                return resolve(tenantId);
            }
        };
        return new PublicChatServiceImpl(promptTemplateService, promptGrantResolver, providerResolution, turnGateway, turnOutcomeMapper, quotaGuard);
    }

    private void auth(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, null));
    }

    private void requestAttrs(List<String> pcode, Map<String, Integer> plock, Map<String, Integer> plim) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (pcode != null) req.setAttribute("publicapi.pcode", pcode);
        if (plock != null) req.setAttribute("publicapi.plock", plock);
        if (plim != null) req.setAttribute("publicapi.plim", plim);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @org.junit.jupiter.api.BeforeEach
    void stubQuotaDecision() {
        lenient().when(quotaGuard.acquireTenant(any(), anyInt()))
                .thenReturn(new QuotaDecision(100, 99, 0, 0, false));
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private ChatCompletionRequestDTO req(String role, String content, String promptCode, String model, Boolean stream) {
        ChatCompletionRequestDTO r = new ChatCompletionRequestDTO();
        r.setPrompt_code(promptCode == null ? "p" : promptCode);
        r.setModel(model);
        r.setStream(stream);
        ChatCompletionRequestDTO.ChatMessageDTO m = new ChatCompletionRequestDTO.ChatMessageDTO();
        m.setRole(role == null ? "user" : role);
        m.setContent(content == null ? "hi" : content);
        r.setMessages(List.of(m));
        return r;
    }

    @Test
    void when_streamTrue_then_illegalArgumentException() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, Boolean.TRUE)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("stream_not_supported");
            return null;
        });
    }

    @Test
    void when_systemRole_then_systemRoleNotAllowed() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("system", "hi", "p", null, null)))
                    .isInstanceOf(SystemRoleNotAllowedException.class);
            return null;
        });
    }

    @Test
    void when_tooManyMessages_then_illegalArgumentException() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 1, "c", 32000, "t", 256000));
        ChatCompletionRequestDTO r = req("user", "hi", "p", null, null);
        ChatCompletionRequestDTO.ChatMessageDTO m2 = new ChatCompletionRequestDTO.ChatMessageDTO();
        m2.setRole("user"); m2.setContent("x");
        r.setMessages(List.of(r.getMessages().get(0), m2));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("too_many_messages");
            return null;
        });
    }

    @Test
    void when_requestTooLarge_then_illegalArgumentException() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 1, "t", 256000));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("request_too_large");
            return null;
        });
    }

    @Test
    void when_notInAllowlist_then_promptNotInAllowlist() throws Exception {
        auth("c");
        requestAttrs(List.of("other"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(PromptNotInAllowlistException.class);
            return null;
        });
    }

    @Test
    void when_quotaExceeded_then_quotaException() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        doThrow(new QuotaExceededException("q", 5)).when(quotaGuard).acquireTenant(any(), anyInt());
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(QuotaExceededException.class);
            return null;
        });
    }

    @Test
    void when_promptNotFound_then_promptNotFound() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        when(promptGrantResolver.resolve(any(), any(), any())).thenThrow(new PromptNotFoundException());
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(PromptNotFoundException.class);
            return null;
        });
    }

    @Test
    void when_promptVersionNotFound_then_promptVersionNotFound() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of("p", 1), Map.of("m", 200, "c", 32000, "t", 256000));
        when(promptGrantResolver.resolve(any(), any(), any())).thenThrow(new PromptVersionNotFoundException());
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(PromptVersionNotFoundException.class);
            return null;
        });
    }

    @Test
    void when_promptNotActive_then_promptNotActive() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        when(promptGrantResolver.resolve(any(), any(), any())).thenThrow(new PromptNotActiveException());
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(PromptNotActiveException.class);
            return null;
        });
    }

    @Test
    void when_unknownModel_then_unknownModel() throws Exception {
        auth("c");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        when(promptGrantResolver.resolve(any(), any(), any())).thenReturn(new PromptTemplate());
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", "other-model", null)))
                    .isInstanceOf(UnknownModelException.class);
            return null;
        });
    }

    /**
     * ADR-020/K11: o chamador público e o human o usam a MESMA política de conversão
     * ({@code history.api.ProviderMessages.toProviderMessages}) e o MESMO gateway/proxy. Antes esta
     * classe montava AiMessage/UserMessage na mão — a divergência K11 que a ADR pede para unificar.
     */
    @Test
    void when_publicCompletionSucceeds_then_conversationUsesTheSamePolicyAsTheHumanCaller() throws Exception {
        auth("client");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        PromptTemplate prompt = new PromptTemplate();
        prompt.setId(UUID.randomUUID());
        prompt.setCode("p");
        prompt.setVersion(1);
        prompt.setTemplate("system do tenant");
        when(promptGrantResolver.resolve(any(), any(), any())).thenReturn(prompt);
        when(turnGateway.execute(any())).thenReturn(
                TurnReply.answered("resposta", 2, 20, 10, 5, 15, "STOP"));

        ChatCompletionRequestDTO r = req("user", "pergunta atual", "p", null, null);
        ChatCompletionRequestDTO.ChatMessageDTO previous = new ChatCompletionRequestDTO.ChatMessageDTO();
        previous.setRole("assistant");
        previous.setContent("resposta antiga");
        r.setMessages(List.of(previous, r.getMessages().get(0)));

        ChatCompletionResponse response = TenantContext.runWithTenant(UUID.randomUUID(), () -> svc().complete(r));

        ArgumentCaptor<TurnRequest> captor = ArgumentCaptor.forClass(TurnRequest.class);
        verify(turnGateway).execute(captor.capture());
        TurnRequest turn = captor.getValue();
        assertThat(turn.conversation()).hasSize(2);
        assertThat(turn.conversation().get(0)).isInstanceOf(dev.langchain4j.data.message.AiMessage.class);
        assertThat(turn.conversation().get(1)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(((dev.langchain4j.data.message.AiMessage) turn.conversation().get(0)).text())
                .isEqualTo("resposta antiga");
        assertThat(((dev.langchain4j.data.message.UserMessage) turn.conversation().get(1)).singleText())
                .isEqualTo("pergunta atual");
        // ADR-001 item 4: role=system rejeitado antes, e metadata.hive presente na resposta de sucesso.
        assertThat(response.getMetadata()).isNotNull();
    }

    /**
     * ADR-001/CT03: congela a serialização da response antes da troca do {@code Map} por DTOs
     * tipados. O teste serializa o objeto retornado sem conhecer seu tipo, então continua válido
     * depois da refatoração — qualquer divergência de JSON quebra o contrato OpenAI.
     */
    @Test
    void when_completeSucceeds_then_serializationPreservesContractShape() throws Exception {
        auth("client");
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        UUID tenantId = UUID.randomUUID();
        PromptTemplate prompt = new PromptTemplate();
        prompt.setId(UUID.randomUUID());
        prompt.setCode("p");
        prompt.setVersion(1);
        prompt.setTemplate("tpl");
        when(promptGrantResolver.resolve(any(), any(), any())).thenReturn(prompt);
        when(turnGateway.execute(any())).thenReturn(
                TurnReply.answered("resposta", 2, 20, 10, 5, 15, "STOP"));

        String json = TenantContext.runWithTenant(tenantId, () -> {
            try {
                Object result = svc().complete(req("user", "hi", "p", null, null));
                return new ObjectMapper().writeValueAsString(result);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThat(root.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "id", "object", "created", "model", "choices", "usage", "prompt_code", "metadata");
        assertThat(root.path("object").asText()).isEqualTo("chat.completion");
        assertThat(root.path("model").asText()).isEqualTo("gpt-4");
        assertThat(root.path("prompt_code").asText()).isEqualTo("p");
        assertThat(root.path("id").asText()).startsWith("chatcmpl-");
        assertThat(root.path("created").isNumber()).isTrue();

        JsonNode choice = root.path("choices").get(0);
        assertThat(root.path("choices")).hasSize(1);
        assertThat(choice.path("index").asInt()).isZero();
        assertThat(choice.path("message").path("role").asText()).isEqualTo("assistant");
        assertThat(choice.path("message").path("content").asText()).isEqualTo("resposta");
        assertThat(choice.path("finish_reason").asText()).isEqualTo("stop");

        assertThat(root.path("usage").path("prompt_tokens").asInt()).isEqualTo(10);
        assertThat(root.path("usage").path("completion_tokens").asInt()).isEqualTo(5);
        assertThat(root.path("usage").path("total_tokens").asInt()).isEqualTo(15);

        JsonNode hive = root.path("metadata").path("hive");
        assertThat(hive.path("turn_id").asText()).isNotBlank();
        assertThat(hive.path("prompt_code").asText()).isEqualTo("p");
        assertThat(hive.path("prompt_version").asInt()).isEqualTo(1);
        assertThat(hive.path("prompt_fingerprint").asText()).startsWith("sha256:");
        assertThat(hive.path("pin_mode").asText()).isEqualTo("FOLLOW_ACTIVE");
        assertThat(hive.path("provider").asText()).isEqualTo("OPENAI");
        assertThat(hive.path("model").asText()).isEqualTo("gpt-4");
        assertThat(hive.path("elapsed_ms").isNumber()).isTrue();
        assertThat(hive.path("tenant_id").asText()).isEqualTo(tenantId.toString());
    }

    @Test
    void when_currentClientIdNull_then_invalidClient() throws Exception {
        SecurityContextHolder.clearContext();
        requestAttrs(List.of("p"), Map.of(), Map.of("m", 200, "c", 32000, "t", 256000));
        TenantContext.runWithTenant(UUID.randomUUID(), () -> {
            assertThatThrownBy(() -> svc().complete(req("user", "hi", "p", null, null)))
                    .isInstanceOf(InvalidClientException.class);
            return null;
        });
    }
}