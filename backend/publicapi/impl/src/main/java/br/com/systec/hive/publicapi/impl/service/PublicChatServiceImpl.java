package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.history.api.util.ProviderMessages;
import br.com.systec.hive.assistant.impl.service.ProviderResolution;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotActiveException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.PromptVersionNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.SystemRoleNotAllowedException;
import br.com.systec.hive.publicapi.api.exceptions.UnknownModelException;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PublicChatServiceImpl implements PublicChatService {

    private final PromptTemplateService promptTemplateService;
    private final PromptGrantResolver promptGrantResolver;
    private final ProviderResolution providerResolution;
    private final ChatTurnGateway turnGateway;
    private final TurnOutcomeMapper turnOutcomeMapper;
    private final RedisQuotaGuard quotaGuard;

    public PublicChatServiceImpl(PromptTemplateService promptTemplateService,
                                 PromptGrantResolver promptGrantResolver,
                                 ProviderResolution providerResolution,
                                 ChatTurnGateway turnGateway,
                                 TurnOutcomeMapper turnOutcomeMapper,
                                 RedisQuotaGuard quotaGuard) {
        this.promptTemplateService = promptTemplateService;
        this.promptGrantResolver = promptGrantResolver;
        this.providerResolution = providerResolution;
        this.turnGateway = turnGateway;
        this.turnOutcomeMapper = turnOutcomeMapper;
        this.quotaGuard = quotaGuard;
    }

    @Override
    public Object complete(br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO request) {
        br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO req = request;
        // validation handled by @Valid in controller; extra checks here
        if (req.getStream() != null && Boolean.TRUE.equals(req.getStream())) {
            throw new IllegalArgumentException("stream_not_supported");
        }
        for (ChatCompletionRequestDTO.ChatMessageDTO m : req.getMessages()) {
            if ("system".equalsIgnoreCase(m.getRole())) {
                throw new SystemRoleNotAllowedException();
            }
        }

        String clientId = currentClientId();
        if (clientId == null) {
            throw new br.com.systec.hive.publicapi.api.exceptions.InvalidClientException("missing client");
        }
        UUID tenantId = TenantContext.getCurrentTenant();

        // claim data from request attributes (set by filter)
        HttpServletRequest httpRequest = null;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            httpRequest = attrs.getRequest();
        }
        List<String> allowlist = new java.util.ArrayList<>();
        Map<String, Integer> pinned = new java.util.HashMap<>();
        Map<String, Integer> limits = new java.util.HashMap<>();
        if (httpRequest != null) {
            Object pcode = httpRequest.getAttribute("publicapi.pcode");
            if (pcode instanceof List) {
                for (Object o : (List<?>) pcode) {
                    if (o != null) allowlist.add(String.valueOf(o));
                }
            }
            Object plock = httpRequest.getAttribute("publicapi.plock");
            if (plock instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) plock).entrySet()) {
                    if (e.getKey() != null && e.getValue() instanceof Number) {
                        pinned.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
                    }
                }
            }
            Object plim = httpRequest.getAttribute("publicapi.plim");
            if (plim instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) plim).entrySet()) {
                    if (e.getKey() != null && e.getValue() instanceof Number) {
                        limits.put(String.valueOf(e.getKey()), ((Number) e.getValue()).intValue());
                    }
                }
            }
        }
        int maxMessages = limit(limits, "m", 200);
        int maxChars = limit(limits, "c", 32000);
        int maxTotal = limit(limits, "t", 256000);

        // validate messages against plim
        if (req.getMessages().size() > maxMessages) {
            throw new IllegalArgumentException("too_many_messages");
        }
        int total = 0;
        for (ChatCompletionRequestDTO.ChatMessageDTO m : req.getMessages()) {
            if (m.getContent() == null || m.getContent().trim().isEmpty() || m.getContent().length() > maxChars) {
                throw new IllegalArgumentException("request_too_large");
            }
            total += m.getContent().length();
        }
        if (total > maxTotal) {
            throw new IllegalArgumentException("request_too_large");
        }

        // quota before prompt
        quotaGuard.checkClient(clientId, 60);

        // allowlist before lookup
        if (!allowlist.contains(req.getPrompt_code())) {
            throw new PromptNotInAllowlistException();
        }

        // resolve prompt inside runWithTenant (already in context)
        PromptTemplate prompt = promptGrantResolver.resolve(req.getPrompt_code(), allowlist, pinned);

        // unknown model check
        ProviderResolution.Resolved resolved = providerResolution.resolve(tenantId);
        if (req.getModel() != null && !req.getModel().isBlank()
                && !req.getModel().equals(resolved.model())) {
            throw new UnknownModelException();
        }

        // ADR-020/K11: mesma conversão do caminho humano. Antes este chamador montava AiMessage/
        // UserMessage na mão e passava history vazio; os dois chamadores discordavam sobre o prompt.
        // Agora ambos produzem List<ChatMessage> via ProviderMessages.toProviderMessages e entregam
        // a mesma TurnRequest ao mesmo proxy (role=system já rejeitado acima, com 400).
        List<br.com.systec.hive.history.api.domain.ChatMessage> conversationInput = new ArrayList<>();
        for (ChatCompletionRequestDTO.ChatMessageDTO m : req.getMessages()) {
            br.com.systec.hive.history.api.domain.ChatMessage entry =
                    new br.com.systec.hive.history.api.domain.ChatMessage();
            entry.setRole("assistant".equalsIgnoreCase(m.getRole())
                    ? br.com.systec.hive.provider.api.ChatRole.ASSISTANT
                    : br.com.systec.hive.provider.api.ChatRole.USER);
            entry.setContent(m.getContent());
            conversationInput.add(entry);
        }
        List<dev.langchain4j.data.message.ChatMessage> conversation =
                br.com.systec.hive.history.api.util.ProviderMessages.toProviderMessages(conversationInput);

        UUID turnId = UUID.randomUUID();
        long started = System.nanoTime();
        TurnReply reply = turnGateway.execute(new TurnRequest(turnId, TurnPrompts.from(prompt), resolved.provider(),
                resolved.model(), resolved.apiKey(), resolved.baseUrl(), conversation));
        long elapsed = (System.nanoTime() - started) / 1_000_000L;

        // outcome mapping (throws on failure/empty)
        turnOutcomeMapper.resolve(null, tenantId, prompt.getId(), resolved.provider(),
                resolved.model(), prompt, List.of(), reply, elapsed);

        String text = reply.text();
        String fingerprint = "sha256:" + sha256(prompt.getTemplate() == null ? "" : prompt.getTemplate());

        Map<String, Object> usage = new HashMap<>();
        usage.put("prompt_tokens", reply.promptTokens());
        usage.put("completion_tokens", reply.completionTokens());
        usage.put("total_tokens", reply.totalTokens());

        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", text);

        Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", reply.finishReason() == null ? "stop" : reply.finishReason().toLowerCase());

        Map<String, Object> hive = new HashMap<>();
        hive.put("turn_id", turnId.toString());
        hive.put("prompt_code", prompt.getCode());
        hive.put("prompt_version", prompt.getVersion());
        hive.put("prompt_fingerprint", fingerprint);
        hive.put("pin_mode", pinned.containsKey(req.getPrompt_code()) ? "PINNED" : "FOLLOW_ACTIVE");
        hive.put("provider", resolved.provider().name());
        hive.put("model", resolved.model());
        hive.put("elapsed_ms", elapsed);
        hive.put("tenant_id", tenantId.toString());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("hive", hive);

        Map<String, Object> response = new HashMap<>();
        response.put("id", "chatcmpl-" + turnId.toString().substring(0, 8));
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", resolved.model());
        response.put("choices", List.of(choice));
        response.put("usage", usage);
        response.put("prompt_code", prompt.getCode());
        response.put("metadata", metadata);

        return response;
    }

    private String currentClientId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private int limit(Map<String, Integer> m, String key, int def) {
        if (m == null) return def;
        Integer v = m.get(key);
        return v == null ? def : v;
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}