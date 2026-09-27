package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.domain.ChatRequest;
import br.com.systec.hive.agent.api.domain.ChatResponse;
import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.domain.InferenceParams;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.domain.TurnReply;
import br.com.systec.hive.agent.api.domain.TurnRequest;
import br.com.systec.hive.agent.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.agent.api.exceptions.ModelUnavailableException;
import br.com.systec.hive.agent.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ProviderException;
import br.com.systec.hive.agent.api.port.ChatTurnGateway;
import br.com.systec.hive.agent.api.service.ChatMessageService;
import br.com.systec.hive.agent.api.service.ChatService;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.impl.diagnostics.FailureClassifier;
import br.com.systec.hive.agent.impl.util.ChatTitleUtil;
import br.com.systec.hive.agent.impl.util.SecurityUtils;
import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import br.com.systec.hive.provider.api.VendorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A chat turn: session, two transactions, one answer, and the line that explains it.
 *
 * <h3>What this class is for, now that the seams exist</h3>
 *
 * <p>It used to hold six responsibilities and fifteen collaborators. One of them was large enough to
 * be somebody else's job and is now a collaborator with one method each: {@link ChatTurnGateway}
 * owns the prompt, the provider call and the reply. What is left is the part that genuinely belongs to
 * no one else — the ordering, the two transactions, and the telemetry.
 *
 * <p><b>The diagnostics stayed here on purpose.</b> {@link #recordTurn} and the two log lines below
 * are what found the six defects this flow was validated against, and they are the only things that
 * can see a session id and a tenant at the same time. A generation seam that also emitted
 * them would have been a seam that owned the explanation, and an explanation with two owners is the
 * condition that produced the bugs in the first place.
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
 * template is what the user picked when the conversation started. The <b>provider and model</b> are a
 * property of the tenant: there is exactly one row, read through
 * {@link TenantLlmConfigService#require}, which throws rather than falling back. Both used to be read
 * off the agent, and the two readings had different lifetimes — a prompt that could differ per
 * conversation and a model that could not — which is the whole reason the agent had to exist.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /**
     * Failure label written to the trace when the provider answered 200 with no usable text. It is a
     * literal rather than a {@code Throwable} because {@link FailureClassifier} classifies from
     * a closed set of exception codes and would degrade it to {@code ...:unexpected}, hiding the one
     * fact the operator needs: the model returned nothing on purpose, it did not crash.
     */
    static final String EMPTY_MODEL_RESPONSE = "empty_model_response";

    private final CredentialResolver credentialResolver;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatAssistantTxService chatAssistantTxService;
    private final TenantLlmConfigService tenantLlmConfigService;
    private final ChatRateLimiter chatRateLimiter;
    private final OpencodeDiscoveryService discoveryService;
    private final ChatTraceRecorder traceRecorder;
    private final ChatTurnGateway turnGateway;

    @Autowired
    public ChatServiceImpl(CredentialResolver credentialResolver,
                           ChatSessionService chatSessionService,
                           ChatMessageService chatMessageService,
                           ChatAssistantTxService chatAssistantTxService,
                           TenantLlmConfigService tenantLlmConfigService,
                           ChatRateLimiter chatRateLimiter,
                           OpencodeDiscoveryService discoveryService,
                           ChatTraceRecorder traceRecorder,
                           ChatTurnGateway turnGateway) {
        this.credentialResolver = credentialResolver;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.chatAssistantTxService = chatAssistantTxService;
        this.tenantLlmConfigService = tenantLlmConfigService;
        this.chatRateLimiter = chatRateLimiter;
        this.discoveryService = discoveryService;
        this.traceRecorder = traceRecorder == null ? ChatTraceRecorder.noop() : traceRecorder;
        this.turnGateway = turnGateway;
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

        if (request.getSessionId() == null) {
            // A new conversation has to arrive with the prompt it will be answered by. There is no
            // default prompt and no template-less conversation: the client resolves the template
            // before it opens the session, exactly as it used to resolve the default agent.
            if (request.getPromptTemplateId() == null) {
                throw new IllegalArgumentException("promptTemplateId is required");
            }
            String title = ChatTitleUtil.truncate(request.getMessage(), 50);
            session = chatSessionService.create(request.getPromptTemplateId(), title, tenantId, userId);
            sessionId = session.getId();
            log.info("chat new session sessionId={} userId={} tenantId={} promptTemplateId={}",
                    sessionId, userId, tenantId, request.getPromptTemplateId());
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
            log.info("chat existing session sessionId={} userId={} tenantId={} promptTemplateId={}",
                    sessionId, userId, tenantId, session.getPromptTemplateId());
        }

        UUID promptTemplateId = session.getPromptTemplateId();
        PromptTemplate promptTemplate = chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId);
        if (promptTemplate == null) {
            throw new PromptTemplateNotFoundException(promptTemplateId);
        }

        // The provider and the model are the tenant's, not the conversation's. `require` throws
        // TenantLlmConfigNotFoundException (422) when the tenant has not configured them: there is
        // deliberately no fallback, because a silent default would turn a missing configuration into
        // a conversation answered by a model nobody chose and nobody is paying for.
        TenantLlmConfig llmConfig = tenantLlmConfigService.require(tenantId);
        AiProvider provider = llmConfig.getProvider();
        String model = llmConfig.getModel();
        VendorCatalog.validate(provider, model);

        // Ponte credential->chat: resolve (tenant, vendor) -> apiKey/baseUrl; ausente => 422 CONFIG_MISSING.
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(tenantId, provider);

        // TX1: save user message
        chatMessageService.save(sessionId, tenantId, ChatRole.USER, request.getMessage());

        // Outside TX: history + provider call
        List<ChatMessage> history = chatMessageService.findLast20(sessionId, tenantId);
        // Converted once and reused: the gateway's prompt is built from it.
        List<dev.langchain4j.data.message.ChatMessage> conversation = toProviderMessages(history);

        TurnReply reply;
        // Measured from here so elapsedMs isolates what the provider is asked to do — a large value
        // here means the provider stalled.
        long startedAt = System.nanoTime();
        reply = turnGateway.execute(new TurnRequest(sessionId, promptTemplate, provider, model,
                credential.apiKey(), credential.baseUrl(), history, conversation));

        if (reply.status() == TurnReply.Status.EMPTY_RESPONSE) {
            // Verified empty BEFORE persisting: chat_message.content is NOT NULL, so saving this
            // turned a provider quirk into a 409 data-integrity error that told the user nothing.
            // Reasoning models are the trigger — mimo-v2.5 with max_tokens=32 spends the whole
            // budget on reasoning and returns content=null; glm-5.2 returns "". Both are covered.
            recordTurn(sessionId, promptTemplateId, provider, model, promptTemplate, history, reply,
                    elapsedMs(startedAt), EMPTY_MODEL_RESPONSE);
            log.warn("chat empty model response sessionId={} tenantId={} promptTemplateId={} provider={} model={} maxTokens={}",
                    sessionId, tenantId, promptTemplateId, provider, model, maxTokensOf(promptTemplate));
            throw new ModelReturnedEmptyException(model);
        }

        if (reply.status() == TurnReply.Status.FAILED) {
            // The gateway hands back the provider's own exception rather than throwing it, because
            // the trace for this turn still needs the size of the prompt that was sent. Everything
            // below is the failure branch this class always had, reading the same throwable it
            // would have caught: same classification, same trace label, same two log lines, same
            // 422-vs-502 decision.
            Throwable failure = reply.failure();
            if (failure instanceof br.com.systec.hive.commons.api.exceptions.BaseException base) {
                throw base;
            }
            recordTurn(sessionId, promptTemplateId, provider, model, promptTemplate, history, reply,
                    elapsedMs(startedAt), FailureClassifier.causeOf(failure));
            if (LangChain4jTurnGateway.isModelNotFound(failure)) {
                if (discoveryService != null) {
                    discoveryService.evict(provider);
                }
                log.warn("chat model unavailable sessionId={} tenantId={} promptTemplateId={} provider={} model={}",
                        sessionId, tenantId, promptTemplateId, provider, model);
                throw new ModelUnavailableException(model, String.valueOf(provider));
            }
            log.error("provider failure sessionId={} tenantId={} promptTemplateId={}", sessionId, tenantId, promptTemplateId, failure);
            throw new ProviderException("LLM provider failure", failure);
        }

        // Closed on purpose. The two cases above return by throwing, so arriving here with either one
        // means the enum grew a member this method does not know about — or an alternative
        // implementation of the port returned null. Falling through on that would reach the save
        // with a null text, and `chat_message.content` is NOT NULL: a wiring mistake would surface as
        // the 409 data-integrity error this class exists to prevent, about a turn nobody diagnosed.
        if (reply.status() != TurnReply.Status.ANSWERED) {
            throw new IllegalStateException("unreachable turn status: " + reply.status());
        }

        recordTurn(sessionId, promptTemplateId, provider, model, promptTemplate, history, reply,
                elapsedMs(startedAt), null);

        // TX2: save assistant + touch atomically
        var assistantMsg = chatAssistantTxService.saveAssistantAndTouch(sessionId, tenantId, reply.text());

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSessionId(sessionId);
        chatResponse.setMessage(reply.text());
        chatResponse.setRole(ChatRole.ASSISTANT);
        if (assistantMsg != null && assistantMsg.getCreateAt() != null) {
            chatResponse.setTimestamp(assistantMsg.getCreateAt().toInstant(ZoneOffset.UTC));
        } else {
            chatResponse.setTimestamp(Instant.now());
        }
        return chatResponse;
    }

    /**
     * One telemetry line per turn, success or failure. The failure branch is the important one:
     * it is the only record of which model and which {@code maxTokens} were in effect when the
     * generation stalled — and of the empty-reply case, where the model spent its whole budget on
     * reasoning and returned nothing. The model text itself never reaches this line: it can be huge.
     *
     * <p>Every number here is measured, never reconstructed: the prompt size and the token counts come
     * from the {@link TurnReply} the gateway built from the provider's own response. A trace that
     * rebuilt them would be a second opinion about a call that already happened.
     *
     * @param failureText already-classified failure label, or {@code null} for a healthy turn
     */
    private void recordTurn(UUID sessionId, UUID promptTemplateId, AiProvider provider, String model,
                            PromptTemplate promptTemplate, List<ChatMessage> history, TurnReply reply,
                            long elapsedMs, String failureText) {
        try {
            // A linha de trace não tem campo de retrieval: o produto não recupera nada, e um campo
            // que não pode ser verdadeiro ensina o operador a ler um vocabulário que não descreve mais
            // o sistema. Ver o javadoc de ChatTrace.
            traceRecorder.record(ChatTrace.builder(sessionId, promptTemplateId, provider, model)
                    .agentLimits(maxTokensOf(promptTemplate), topPOf(promptTemplate), temperatureOf(promptTemplate))
                    .prompt(history.size(), reply.promptMessages(), reply.promptChars())
                    .elapsedMs(elapsedMs)
                    .usage(reply.promptTokens(), reply.completionTokens(), reply.totalTokens(),
                            reply.finishReason())
                    .failure(failureText)
                    .build());
        } catch (Exception e) {
            log.warn("chat trace recorder failed sessionId={}", sessionId, e);
        }
    }

    private static Integer maxTokensOf(PromptTemplate template) {
        return inferenceParams(template).getMaxTokens();
    }

    private static Double topPOf(PromptTemplate template) {
        return inferenceParams(template).getTopP();
    }

    private static Double temperatureOf(PromptTemplate template) {
        return inferenceParams(template).getTemperature();
    }

    /** Never {@code null}: a template with no parameters still has three absent ones. */
    private static InferenceParams inferenceParams(PromptTemplate template) {
        return template.getInferenceParams() != null ? template.getInferenceParams() : new InferenceParams();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    /**
     * The conversation in the provider's message type, dropping entries with no content.
     *
     * <p>System messages are kept in the list: a system prompt is an instruction to the model, and
     * the prompt builder places it ahead of the conversation.
     *
     * <p>Converted once per turn and handed to the gateway, which builds the prompt from it —
     * converting one history twice would be two chances to disagree about what was said.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> toProviderMessages(List<ChatMessage> history) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String content = msg.getContent() != null ? msg.getContent() : msg.getMessage();
            if (content == null) {
                continue;
            }
            if (msg.getRole() == ChatRole.ASSISTANT) {
                messages.add(new dev.langchain4j.data.message.AiMessage(content));
            } else if (msg.getRole() == ChatRole.SYSTEM) {
                messages.add(new dev.langchain4j.data.message.SystemMessage(content));
            } else {
                messages.add(new dev.langchain4j.data.message.UserMessage(content));
            }
        }
        return messages;
    }
}
