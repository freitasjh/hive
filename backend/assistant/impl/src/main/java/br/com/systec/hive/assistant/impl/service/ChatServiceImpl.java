package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.domain.ChatSession;
import br.com.systec.hive.assistant.api.domain.PromptStatus;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import br.com.systec.hive.history.api.service.ChatMessageService;
import br.com.systec.hive.assistant.api.service.ChatService;
import br.com.systec.hive.assistant.api.service.ChatSessionService;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.history.api.util.ProviderMessages;
import br.com.systec.hive.assistant.api.util.ProviderOverrideGuard;
import br.com.systec.hive.assistant.impl.entity.PromptTemplateEntity;
import br.com.systec.hive.assistant.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.assistant.impl.util.ChatTitleUtil;
import br.com.systec.hive.assistant.impl.util.SecurityUtils;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A chat turn: session, two transactions, one answer, and the line that explains it.
 *
 * <p>ADR-002 reuses {@link #prepare} on the streaming path: validation, rate limit, session,
 * prompt, provider resolution and the TX1 user message are the same preflight for both transports.
 * Only the generation call after that point differs — sync waits for a reply, SSE streams events.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final String FALLBACK_PROMPT_CODE = "system_fallback";

    private final ProviderResolution providerResolution;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatAssistantTxService chatAssistantTxService;
    private final ChatRateLimiter chatRateLimiter;
    private final ChatTurnGateway turnGateway;
    private final PromptTemplateService promptTemplateService;
    private final PromptTemplateRepository promptTemplateRepository;
    private final TurnOutcomeMapper turnOutcomeMapper;

    @Autowired
    public ChatServiceImpl(ProviderResolution providerResolution,
                           ChatSessionService chatSessionService,
                           ChatMessageService chatMessageService,
                           ChatAssistantTxService chatAssistantTxService,
                           ChatRateLimiter chatRateLimiter,
                           ChatTurnGateway turnGateway,
                           PromptTemplateService promptTemplateService,
                           PromptTemplateRepository promptTemplateRepository,
                           TurnOutcomeMapper turnOutcomeMapper) {
        this.providerResolution = providerResolution;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.chatAssistantTxService = chatAssistantTxService;
        this.chatRateLimiter = chatRateLimiter;
        this.turnGateway = turnGateway;
        this.promptTemplateService = promptTemplateService;
        this.promptTemplateRepository = promptTemplateRepository;
        this.turnOutcomeMapper = turnOutcomeMapper;
    }

    /** Everything up to and including TX1, shared by the sync and the SSE endpoint. */
    public record PreparedTurn(UUID sessionId, PromptTemplate promptTemplate,
                               ProviderResolution.Resolved resolved, List<ChatMessage> history,
                               List<dev.langchain4j.data.message.ChatMessage> conversation) {
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new IllegalStateException("Tenant not found in context", e);
        }
        UUID userId = SecurityUtils.currentUserId();
        return chat(request, tenantId, userId);
    }

    @Override
    public ChatResponse chat(ChatRequest request, UUID tenantId, UUID userId) {
        PreparedTurn prepared = prepare(request, tenantId, userId);
        long startedAt = System.nanoTime();
        TurnReply reply = turnGateway.execute(new TurnRequest(prepared.sessionId(),
                TurnPrompts.from(prepared.promptTemplate()), prepared.resolved().provider(),
                prepared.resolved().model(), prepared.resolved().apiKey(), prepared.resolved().baseUrl(),
                prepared.conversation()));
        turnOutcomeMapper.resolve(prepared.sessionId(), tenantId, prepared.promptTemplate().getId(),
                prepared.resolved().provider(), prepared.resolved().model(), prepared.promptTemplate(),
                prepared.history(), reply, elapsedMs(startedAt), null, null);

        var assistantMsg = chatAssistantTxService.saveAssistantAndTouch(
                prepared.sessionId(), tenantId, reply.text());
        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSessionId(prepared.sessionId());
        chatResponse.setMessage(reply.text());
        chatResponse.setRole(ChatRole.ASSISTANT);
        chatResponse.setProvider(prepared.resolved().provider());
        chatResponse.setModel(prepared.resolved().model());
        if (assistantMsg != null && assistantMsg.getCreateAt() != null) {
            chatResponse.setTimestamp(assistantMsg.getCreateAt().toInstant(ZoneOffset.UTC));
        } else {
            chatResponse.setTimestamp(Instant.now());
        }
        return chatResponse;
    }

    /** Validates, throttles, opens/reuses the session, resolves the turn and saves the user's message. */
    public PreparedTurn prepare(ChatRequest request, UUID tenantId, UUID userId) {
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()
                || request.getMessage().length() > 4000) {
            throw new IllegalArgumentException("message must be between 1 and 4000 characters");
        }
        if (chatRateLimiter != null) {
            chatRateLimiter.check(tenantId);
        }

        UUID sessionId;
        ChatSession session;
        AiProvider effectiveProvider;
        String effectiveModel;

        if (request.getSessionId() == null) {
            UUID effectivePromptTemplateId = request.getPromptTemplateId();
            if (effectivePromptTemplateId == null) {
                try {
                    PromptTemplate fallback = promptTemplateService.findActiveByCode(FALLBACK_PROMPT_CODE);
                    if (fallback != null && fallback.getId() != null) {
                        effectivePromptTemplateId = fallback.getId();
                    }
                } catch (PromptTemplateNotFoundException notSeeded) {
                    log.info("chat fallback not seeded, ensuring technical record tenantId={}", tenantId);
                }
                if (effectivePromptTemplateId == null) {
                    effectivePromptTemplateId = ensureFallbackPromptTemplateId(tenantId);
                }
            }
            String title = ChatTitleUtil.truncate(request.getMessage(), 50);
            effectiveProvider = request.getProvider();
            effectiveModel = request.getModel();
            if (effectiveProvider != null || effectiveModel != null) {
                ProviderOverrideGuard.validate(effectiveProvider, effectiveModel);
                session = chatSessionService.create(effectivePromptTemplateId, title, tenantId,
                        userId, effectiveProvider, effectiveModel);
            } else {
                session = chatSessionService.create(effectivePromptTemplateId, title, tenantId, userId);
            }
            sessionId = session.getId();
            log.info("chat new session sessionId={} userId={} tenantId={} promptTemplateId={} provider={}",
                    sessionId, userId, tenantId, effectivePromptTemplateId, effectiveProvider);
        } else {
            sessionId = request.getSessionId();
            session = chatSessionService.getSession(sessionId, tenantId, userId);
            if (request.getPromptTemplateId() != null
                    && !request.getPromptTemplateId().equals(session.getPromptTemplateId())) {
                log.info("chat ignore divergent promptTemplateId request={} session={} sessionId={}",
                        request.getPromptTemplateId(), session.getPromptTemplateId(), sessionId);
            }
            effectiveProvider = session.getProvider();
            effectiveModel = session.getModel();
            if ((request.getProvider() != null && request.getProvider() != effectiveProvider)
                    || (request.getModel() != null && !request.getModel().equals(effectiveModel))) {
                log.info("chat ignore divergent provider/model request={}/{} session={}/{} sessionId={}",
                        request.getProvider(), request.getModel(), effectiveProvider, effectiveModel, sessionId);
            }
            log.info("chat existing session sessionId={} userId={} tenantId={} promptTemplateId={}",
                    sessionId, userId, tenantId, session.getPromptTemplateId());
        }

        UUID promptTemplateId = session.getPromptTemplateId();
        PromptTemplate promptTemplate = chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId);
        if (promptTemplate == null) {
            throw new PromptTemplateNotFoundException(promptTemplateId);
        }
        ProviderResolution.Resolved resolved = providerResolution.resolve(tenantId, effectiveProvider, effectiveModel);

        chatMessageService.save(sessionId, tenantId, ChatRole.USER, request.getMessage());
        List<ChatMessage> history = chatMessageService.findLast20(sessionId, tenantId);
        return new PreparedTurn(sessionId, promptTemplate, resolved, history,
                ProviderMessages.toProviderMessages(history));
    }

    private UUID ensureFallbackPromptTemplateId(UUID tenantId) {
        Optional<PromptTemplateEntity> active = promptTemplateRepository
                .findByCodeAndTenantIdAndStatus(FALLBACK_PROMPT_CODE, tenantId, PromptStatus.ACTIVE);
        if (active.isPresent()) {
            return active.get().getId();
        }
        Optional<PromptTemplateEntity> existing = promptTemplateRepository
                .findFirstByCodeAndTenantIdOrderByVersionDesc(FALLBACK_PROMPT_CODE, tenantId);
        if (existing.isPresent()) {
            PromptTemplateEntity reuse = existing.get();
            reuse.setTemplate(AssistantTurnGateway.PROMPT);
            reuse.setStatus(PromptStatus.ACTIVE);
            return saveFallbackTemplate(reuse);
        }
        PromptTemplateEntity created = new PromptTemplateEntity();
        created.setTenantId(tenantId);
        created.setCode(FALLBACK_PROMPT_CODE);
        created.setName("Prompt de sistema (fallback)");
        created.setTemplate(AssistantTurnGateway.PROMPT);
        created.setVersion(1);
        created.setStatus(PromptStatus.ACTIVE);
        return saveFallbackTemplate(created);
    }

    private UUID saveFallbackTemplate(PromptTemplateEntity template) {
        try {
            return promptTemplateRepository.save(template).getId();
        } catch (DataIntegrityViolationException concurrentInsert) {
            return promptTemplateRepository.findByCodeAndTenantIdAndStatus(
                            FALLBACK_PROMPT_CODE, template.getTenantId(), PromptStatus.ACTIVE)
                    .or(() -> promptTemplateRepository.findFirstByCodeAndTenantIdOrderByVersionDesc(
                            FALLBACK_PROMPT_CODE, template.getTenantId()))
                    .map(PromptTemplateEntity::getId)
                    .orElseThrow(() -> concurrentInsert);
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
