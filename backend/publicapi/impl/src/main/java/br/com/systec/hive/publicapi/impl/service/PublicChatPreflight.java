package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.assistant.api.service.TurnPolicyResolver;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.history.api.util.ProviderMessages;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.SystemRoleNotAllowedException;
import br.com.systec.hive.publicapi.api.exceptions.UnknownModelException;
import br.com.systec.hive.publicapi.impl.quota.QuotaDecision;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The unchanged public preflight (ADR-002 E2), shared by the sync and SSE paths so a streaming
 * request cannot bypass validation, allowlist, quota, prompt resolution or model resolution.
 * Server-side generation ceilings are applied here, before any emitter is opened (F2).
 */
@Component
public final class PublicChatPreflight {

    /** Server-side ceiling for a streaming turn; a client cannot raise it through the prompt. */
    public static final int MAX_TOKENS = 4096;
    public static final int MAX_FRAMES = 2000;

    private final PromptTemplateService promptTemplateService;
    private final PromptGrantResolver promptGrantResolver;
    private final TurnPolicyResolver providerResolution;
    private final RedisQuotaGuard quotaGuard;

    public PublicChatPreflight(PromptTemplateService promptTemplateService,
                               PromptGrantResolver promptGrantResolver,
                               TurnPolicyResolver providerResolution,
                               RedisQuotaGuard quotaGuard) {
        this.promptTemplateService = promptTemplateService;
        this.promptGrantResolver = promptGrantResolver;
        this.providerResolution = providerResolution;
        this.quotaGuard = quotaGuard;
    }

    public record Plan(UUID turnId, UUID tenantId, String clientId, PromptTemplate prompt,
                       TurnPrompt turnPrompt, TurnPolicyResolver.Resolved resolved,
                       List<dev.langchain4j.data.message.ChatMessage> conversation,
                       String fingerprint, boolean pinned, String providerName,
                       br.com.systec.hive.ai.api.domain.TurnRequest turnRequest) {
    }

    public Plan of(ChatCompletionRequestDTO request) {
        for (ChatCompletionRequestDTO.ChatMessageDTO m : request.getMessages()) {
            if ("system".equalsIgnoreCase(m.getRole())) {
                throw new SystemRoleNotAllowedException();
            }
        }

        String clientId = currentClientId();
        if (clientId == null) {
            throw new InvalidClientException("missing client");
        }
        UUID tenantId = TenantContext.getCurrentTenant();

        HttpServletRequest httpRequest = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            httpRequest = attrs.getRequest();
        }
        List<String> allowlist = new ArrayList<>();
        Map<String, Integer> pinned = new HashMap<>();
        Map<String, Integer> limits = new HashMap<>();
        if (httpRequest != null) {
            Object pcode = httpRequest.getAttribute("publicapi.pcode");
            if (pcode instanceof List<?> codes) {
                for (Object o : codes) {
                    if (o != null) allowlist.add(String.valueOf(o));
                }
            }
            Object plock = httpRequest.getAttribute("publicapi.plock");
            if (plock instanceof Map<?, ?> locks) {
                for (Map.Entry<?, ?> e : locks.entrySet()) {
                    if (e.getKey() != null && e.getValue() instanceof Number n) {
                        pinned.put(String.valueOf(e.getKey()), n.intValue());
                    }
                }
            }
            Object plim = httpRequest.getAttribute("publicapi.plim");
            if (plim instanceof Map<?, ?> lim) {
                for (Map.Entry<?, ?> e : lim.entrySet()) {
                    if (e.getKey() != null && e.getValue() instanceof Number n) {
                        limits.put(String.valueOf(e.getKey()), n.intValue());
                    }
                }
            }
        }
        int maxMessages = limit(limits, "m", 200);
        int maxChars = limit(limits, "c", 32000);
        int maxTotal = limit(limits, "t", 256000);

        if (request.getMessages().size() > maxMessages) {
            throw new IllegalArgumentException("too_many_messages");
        }
        int total = 0;
        for (ChatCompletionRequestDTO.ChatMessageDTO m : request.getMessages()) {
            if (m.getContent() == null || m.getContent().trim().isEmpty() || m.getContent().length() > maxChars) {
                throw new IllegalArgumentException("request_too_large");
            }
            total += m.getContent().length();
        }
        if (total > maxTotal) {
            throw new IllegalArgumentException("request_too_large");
        }

        Object keyId = httpRequest == null ? null : httpRequest.getAttribute("publicapi.keyId");
        Object keyLimit = httpRequest == null ? null : httpRequest.getAttribute("publicapi.keylim");
        QuotaDecision decision = keyId instanceof String kid && keyLimit instanceof Number kl
                ? quotaGuard.acquireKey(kid, kl.intValue())
                : quotaGuard.acquireTenant(tenantId, limit(limits, "r", 100));
        if (decision.measured() && httpRequest != null) {
            httpRequest.setAttribute("publicapi.quota.limit", decision.limit());
            httpRequest.setAttribute("publicapi.quota.remaining", decision.remaining());
            httpRequest.setAttribute("publicapi.quota.reset", decision.resetEpochSeconds());
        }

        if (!allowlist.contains(request.getPrompt_code())) {
            throw new PromptNotInAllowlistException();
        }
        PromptTemplate prompt = promptGrantResolver.resolve(request.getPrompt_code(), allowlist, pinned);

        TurnPolicyResolver.Resolved resolved = providerResolution.resolve(tenantId);
        if (request.getModel() != null && !request.getModel().isBlank()
                && !request.getModel().equals(resolved.model())) {
            throw new UnknownModelException();
        }

        List<ChatMessage> conversation = new ArrayList<>();
        for (ChatCompletionRequestDTO.ChatMessageDTO m : request.getMessages()) {
            ChatMessage entry = new ChatMessage();
            entry.setRole("assistant".equalsIgnoreCase(m.getRole()) ? ChatRole.ASSISTANT : ChatRole.USER);
            entry.setContent(m.getContent());
            conversation.add(entry);
        }
        List<dev.langchain4j.data.message.ChatMessage> providerMessages =
                ProviderMessages.toProviderMessages(conversation);

        TurnPrompt original = TurnPrompts.from(prompt);
        Integer maxTokens = original.maxTokens() == null ? null : Math.min(original.maxTokens(), MAX_TOKENS);
        TurnPrompt bounded = new TurnPrompt(original.templateId(), original.text(),
                original.temperature(), maxTokens, original.topP());

        UUID turnId = UUID.randomUUID();
        br.com.systec.hive.ai.api.domain.TurnRequest turnRequest =
                new br.com.systec.hive.ai.api.domain.TurnRequest(turnId, bounded,
                        resolved.provider(), resolved.model(), resolved.apiKey(),
                        resolved.baseUrl(), providerMessages);
        return new Plan(turnId, tenantId, clientId, prompt, bounded, resolved,
                providerMessages, "sha256:" + sha256(prompt.getTemplate() == null ? "" : prompt.getTemplate()),
                pinned.containsKey(request.getPrompt_code()), resolved.provider().name(), turnRequest);
    }

    private static String currentClientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private static int limit(Map<String, Integer> m, String key, int def) {
        Integer v = m.get(key);
        return v == null ? def : v;
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
