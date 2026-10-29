package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.domain.TurnReply;
import br.com.systec.hive.agent.api.service.PromptTemplateService;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.agent.impl.service.ProviderResolution;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotActiveException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.PromptVersionNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.SystemRoleNotAllowedException;
import br.com.systec.hive.publicapi.api.exceptions.UnknownModelException;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicChatServiceImplTest {

    @Mock private PromptTemplateService promptTemplateService;
    @Mock private PromptGrantResolver promptGrantResolver;
    // providerResolution is provided by a subclass override below (avoids mocking final-ish classes on JDK 25)
    @Mock private br.com.systec.hive.agent.api.port.ChatTurnGateway turnGateway;
    @Mock private br.com.systec.hive.agent.impl.service.TurnOutcomeMapper turnOutcomeMapper;
    @Mock private RedisQuotaGuard quotaGuard;

    private PublicChatServiceImpl svc() {
        ProviderResolution providerResolution = new ProviderResolution(null, null) {
            @Override
            public Resolved resolve(java.util.UUID tenantId) {
                return new Resolved(br.com.systec.hive.provider.api.AiProvider.OPENAI, "gpt-4", "key", "url");
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
        doThrow(new QuotaExceededException("q", 5)).when(quotaGuard).checkClient(any(), any(Integer.class));
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