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
 * <h3>What this class is for, now that the seams exist</h3>
 *
 * <p>It used to hold six responsibilities and fifteen collaborators. Three of them were large enough
 * to be somebody else's job and are now collaborators with one method each: {@link ChatTurnGateway}
 * owns the prompt, the provider call and the reply; {@link ProviderResolution} owns which provider and
 * model the tenant configured, and the credential that goes with them; {@link TurnOutcomeMapper} owns
 * what each outcome means — the trace line, the operator line and the throwable. What is left is the
 * part that genuinely belongs to no one else: the ordering, the two transactions, and the call that
 * hands a turn to the seam.
 *
 * <p><b>The diagnostics are still about this flow, and are still emitted once per turn.</b> They moved
 * into {@link TurnOutcomeMapper} together with the branches they belong to, because a trace is the
 * record of an outcome and the outcome is what that class now decides. They are unchanged: same line,
 * same fields, same numbers, same order relative to the two transactions. They are what found the six
 * defects this flow was validated against, and they are the only thing that can see a session id and
 * a tenant at the same time. A generation seam that also emitted them would have been a seam that
 * owned the explanation, and an explanation with two owners is the condition that produced the bugs
 * in the first place.
 *
 * <h3>The transaction shape is load-bearing</h3>
 *
 * <p>The user's message is persisted in TX1, <b>before</b> the provider is called, and the assistant's
 * message plus the session touch are persisted together in TX2. A turn that fails to generate
 * therefore keeps the question — which is what the user needs in order to resend it — and never
 * persists an assistant row, because {@code chat_message.content} is {@code NOT NULL} and a blank
 * model reply used to reach that column and surface as a 409 data-integrity error.
 *
 * <h3>Where the two parameters come from</h3>
 *
 * <p>The <b>prompt</b> is a property of the conversation: the session names the template, and the
 * template is what the user picked when the conversation started. The <b>provider and model</b> are
 * now a property of the conversation too when one was chosen — the choice is persisted in
 * {@code chat_session} (V7) and is authoritative on later turns. Without a choice, the tenant's
 * single row is read through {@link ProviderResolution}, which throws rather than falling back.
 * Both used to be read off the agent, and the two readings had different lifetimes — which is the
 * whole reason the agent had to exist.
 *
 * <h3>Who reads the ambient context, and who does not</h3>
 *
 * <p>{@link #chat(ChatRequest)} reads the tenant and the user from the request's environment —
 * {@link TenantContext} and {@link SecurityUtils} — and passes both as parameters.
 *
 * <p>{@link #chat(ChatRequest, UUID, UUID)} reads <b>nothing</b> from the environment: its own
 * queries are derived queries that receive the tenant explicitly, and so does every identity it
 * hands to a collaborator. That is what makes a turn drivable by a caller with no HTTP session
 * behind it, and it is asserted by a test that runs a turn with an empty
 * {@code SecurityContextHolder} rather than by this paragraph alone.
 *
 * <p><b>The guarantee stops at the collaborators' own reads.</b> The new public API path resolves its
 * prompt through {@code PromptTemplateService.findByCodeAndVersion} (batch 1.8), which reads
 * {@code TenantContext}, and {@code TenantAspect} binds the Hibernate {@code @Filter} from
 * {@code TenantContext} as well — not from a parameter. Any new caller therefore has to run its
 * resolution <b>inside</b> {@code TenantContext.runWithTenant} before it reaches this method.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** Seed V8: fallback por tenant quando o cliente não escolhe um template (ADR-020). */
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
        if (request == null || request.getMessage() == null || request.getMessage().isBlank() || request.getMessage().length() > 4000) {
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
            // Cliente sem prompt escolhido não é bloqueado: a conversa usa o fallback ativo do
            // tenant (seed V8/ADR-020). A resolução é tenant-scoped e acontece ANTES de criar a
            // sessão — sem registro ativo, o registro técnico é garantido (nunca erro de fallback).
            UUID effectivePromptTemplateId = request.getPromptTemplateId();
            if (effectivePromptTemplateId == null) {
                // ADR-020 (decisão humana 2026-11-21): o prompt default é código
                // (AssistantTurnGateway.systemFor), não registro de banco. `system_fallback` existe
                // só para a FK; ausência do seed V8 NÃO pode virar erro para o cliente.
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

            // A escolha da UI vem no body (https://ui.shadcn.com/docs/ai-elements/base/model-selector,
            // consultado em 2026-11-20). Validar ANTES de criar a sessão impede que um par inválido
            // seja persistido e herdado por todos os turnos seguintes daquela conversa.
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
                // Not an error: the prompt is a property of the conversation, and a client that
                // still holds a stale one must not be able to retarget a conversation that already
                // has messages. Logged so the mismatch is visible instead of silent.
                log.info("chat ignore divergent promptTemplateId request={} session={} sessionId={}",
                        request.getPromptTemplateId(), session.getPromptTemplateId(), sessionId);
            }
            // A sessão é autoritativa: um cliente que reenvia provider/model no payload não pode
            // trocar silenciosamente a conversa que já tem histórico (mesma regra do prompt).
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

        // The pair is the conversation's when it chose one (persisted in the session), otherwise the
        // tenant's. The three reads behind this call — the tenant's single row (only on the default
        // path), the vendor catalog, and the credential for that vendor — are one decision with an
        // order, and they live in ProviderResolution now. A half pair is 400 before any write; a
        // missing tenant default on the no-override path is 422, never a silent fallback.
        ProviderResolution.Resolved resolved = providerResolution.resolve(tenantId, effectiveProvider, effectiveModel);

        // TX1: save user message
        chatMessageService.save(sessionId, tenantId, ChatRole.USER, request.getMessage());

        // Outside TX: history + provider call
        List<ChatMessage> history = chatMessageService.findLast20(sessionId, tenantId);
        // Converted once and reused: the gateway's prompt is built from it.
        List<dev.langchain4j.data.message.ChatMessage> conversation = ProviderMessages.toProviderMessages(history);

        TurnReply reply;
        // Measured from here so elapsedMs isolates what the provider is asked to do — a large value
        // here means the provider stalled.
        long startedAt = System.nanoTime();
        reply = turnGateway.execute(new TurnRequest(sessionId, TurnPrompts.from(promptTemplate), resolved.provider(),
                resolved.model(), resolved.apiKey(), resolved.baseUrl(), conversation));

        // Every outcome except "answered" raises inside this call: the trace is recorded and the
        // operator's line is written by the collaborator that decided the outcome, so that the
        // classification is stated once. Reaching TX2 therefore means the turn was answered.
        // ADR-002/K6: o fluxo humano não tem turnId nem clientId — null/null explícitos (ChatTrace:26-27).
        turnOutcomeMapper.resolve(sessionId, tenantId, promptTemplateId, resolved.provider(),
                resolved.model(), promptTemplate, history, reply, elapsedMs(startedAt), null, null);

        // TX2: save assistant + touch atomically
        var assistantMsg = chatAssistantTxService.saveAssistantAndTouch(sessionId, tenantId, reply.text());

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSessionId(sessionId);
        chatResponse.setMessage(reply.text());
        chatResponse.setRole(ChatRole.ASSISTANT);
        // O par EFETIVO que respondeu — o frontend não precisa re-derivar o default do tenant.
        chatResponse.setProvider(resolved.provider());
        chatResponse.setModel(resolved.model());
        if (assistantMsg != null && assistantMsg.getCreateAt() != null) {
            chatResponse.setTimestamp(assistantMsg.getCreateAt().toInstant(ZoneOffset.UTC));
        } else {
            chatResponse.setTimestamp(Instant.now());
        }
        return chatResponse;
    }

    /**
     * Garante o registro técnico `system_fallback` do tenant. Idempotente: reusa o ativo (V8),
     * reativa um registro existente em outra versão/status, e só cria quando não há nenhum.
     * O texto é `AssistantTurnGateway.PROMPT` (default em código do ADR-020) — o system efetivo
     * vem do gateway (`systemFor`); aqui o valor apenas satisfaz a coluna `NOT NULL`.
     */
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
            // Texto histórico pode ser não-blank; se ficar, o gateway não aplicaria o default
            // em código. Registro técnico deve sempre carregar o texto padrão (ADR-020).
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

    /** Concorrência: a UNIQUE (tenant, code, version) decide; o perdedor re-lê o vencedor. */
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
