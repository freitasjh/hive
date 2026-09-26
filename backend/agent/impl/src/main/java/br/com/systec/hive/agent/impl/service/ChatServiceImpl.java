package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.domain.ChatRequest;
import br.com.systec.hive.agent.api.domain.ChatResponse;
import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.agent.api.exceptions.AgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.agent.api.exceptions.ModelUnavailableException;
import br.com.systec.hive.agent.api.exceptions.ProviderException;
import br.com.systec.hive.agent.api.service.AgentService;
import br.com.systec.hive.agent.api.service.ChatMessageService;
import br.com.systec.hive.agent.api.service.ChatService;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.api.service.DefaultAgentService;
import br.com.systec.hive.agent.impl.rag.ConditionalCompressingQueryTransformer;
import br.com.systec.hive.agent.impl.rag.FollowUpQueryHeuristic;
import br.com.systec.hive.agent.impl.rag.KnowledgeBaseContentRetriever;
import br.com.systec.hive.agent.impl.rag.RagAugmentorFactory;
import br.com.systec.hive.agent.impl.rag.RetrievedContent;
import br.com.systec.hive.agent.impl.util.ChatTitleUtil;
import br.com.systec.hive.agent.impl.util.SecurityUtils;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.knowledge.api.domain.KbCitation;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException;
import br.com.systec.hive.knowledge.api.port.KbAgentLinkPort;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.query.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /**
     * Package-private so a test can assert the cap this class actually sends, instead of repeating
     * the number and drifting from it.
     */
    static final int RAG_MAX_RESULTS = 5;

    /**
     * Failure label written to the trace when the provider answered 200 with no usable text. It is a
     * literal rather than a {@code Throwable} because {@link RagDiagnostic#causeOf} classifies from
     * a closed set of exception codes and would degrade it to {@code ...:unexpected}, hiding the one
     * fact the operator needs: the model returned nothing on purpose, it did not crash.
     */
    static final String EMPTY_MODEL_RESPONSE = "empty_model_response";

    /**
     * Output budget for the query compressor, when one is configured.
     *
     * <p>The rewrite is a single sentence. The budget is not tight because this project has already
     * shipped the failure a tight budget causes: a reasoning model given a small {@code maxTokens}
     * spends all of it on reasoning and returns no content, which the turn reports as
     * {@code empty_model_response}. 256 leaves room for that reasoning and still returns the sentence.
     */
    static final int COMPRESSION_MAX_TOKENS = 256;

    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final CredentialResolver credentialResolver;
    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatAssistantTxService chatAssistantTxService;
    private final DefaultAgentService defaultAgentService;
    private final KbRetrievalPort kbRetrieval;
    private final KbAgentLinkPort kbLinkPort;
    private final ChatRateLimiter chatRateLimiter;
    private final OpencodeDiscoveryService discoveryService;
    private final ChatTraceRecorder traceRecorder;
    private final RagDiagnosticRecorder ragRecorder;
    private final LlmRetryPolicy retryPolicy;

    /**
     * Builds one augmentor per turn out of components that do not change; the components that do
     * change — retriever, transformer — are passed to it.
     */
    private final RagAugmentorFactory augmentorFactory;

    /**
     * Model allowed to rewrite a follow-up question, or {@code null}/blank to keep the stage off.
     *
     * <p>Off by default on purpose: enabling it adds one LLM call to the turns that look like
     * continuations, and an unmeasured extra call is exactly what the latency baseline forbids. It is
     * also never the agent's own model — see {@link #compressionModel}.
     */
    private final String queryCompressionModel;

    /**
     * Length at or below which a question counts as elliptical for the query compressor.
     *
     * <p>Configurable because it was measured on a hand-labelled set, not on production traffic: see
     * {@link FollowUpQueryHeuristic} for the numbers and for what a wrong value costs.
     */
    private final int ellipticalQuestionLimit;

    static final Pattern MODEL_NOT_FOUND_PATTERN = Pattern.compile(
            "(?i)(model_not_found|no such model|unknown model|\\bmodel\\b.*(not found|does not exist))");

    public ChatServiceImpl(LlmClientFactory<ChatModel> llmClientFactory,
                           CredentialResolver credentialResolver,
                           AgentService agentService,
                           ChatSessionService chatSessionService,
                           ChatMessageService chatMessageService,
                           ChatAssistantTxService chatAssistantTxService,
                           DefaultAgentService defaultAgentService) {
        this(llmClientFactory, credentialResolver, agentService, chatSessionService, chatMessageService,
                chatAssistantTxService, defaultAgentService, null, null, null, null,
                ChatTraceRecorder.noop(), RagDiagnosticRecorder.noop(), null,
                FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);
        log.warn("KB module not available, RAG disabled");
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public ChatServiceImpl(LlmClientFactory<ChatModel> llmClientFactory,
                           CredentialResolver credentialResolver,
                           AgentService agentService,
                           ChatSessionService chatSessionService,
                           ChatMessageService chatMessageService,
                           ChatAssistantTxService chatAssistantTxService,
                           DefaultAgentService defaultAgentService,
                           KbRetrievalPort kbRetrieval,
                           KbAgentLinkPort kbLinkPort,
                           ChatRateLimiter chatRateLimiter,
                           OpencodeDiscoveryService discoveryService,
                           ChatTraceRecorder traceRecorder,
                           RagDiagnosticRecorder ragRecorder,
                           @Value("${hive.rag.query-compression-model:}") String queryCompressionModel,
                           @Value("${hive.rag.elliptical-limit:"
                                   + FollowUpQueryHeuristic.ELLIPTICAL_LIMIT + "}") int ellipticalQuestionLimit) {
        this.llmClientFactory = llmClientFactory;
        this.credentialResolver = credentialResolver;
        this.agentService = agentService;
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.chatAssistantTxService = chatAssistantTxService;
        this.defaultAgentService = defaultAgentService;
        this.kbRetrieval = kbRetrieval;
        this.kbLinkPort = kbLinkPort;
        this.chatRateLimiter = chatRateLimiter;
        this.discoveryService = discoveryService;
        this.traceRecorder = traceRecorder == null ? ChatTraceRecorder.noop() : traceRecorder;
        this.ragRecorder = ragRecorder == null ? RagDiagnosticRecorder.noop() : ragRecorder;
        this.retryPolicy = LlmRetryPolicy.defaults();
        this.augmentorFactory = new RagAugmentorFactory();
        this.queryCompressionModel = queryCompressionModel;
        this.ellipticalQuestionLimit = ellipticalQuestionLimit;
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

        // Fallback for new session when agentId is null -> resolve default
        if (request.getSessionId() == null && (request.getAgent() == null || request.getAgent().getId() == null)) {
            Agent def = defaultAgentService.requireDefault(tenantId);
            Agent agent = new Agent();
            agent.setId(def.getId());
            request.setAgent(agent);
            log.info("chat fallback to default agentId={} tenantId={}", def.getId(), tenantId);
        } else if (request.getAgent() == null || request.getAgent().getId() == null) {
            if (request.getSessionId() == null) {
                throw new IllegalArgumentException("agentId is required");
            }
        }

        UUID sessionId;
        ChatSession session;

        if (request.getSessionId() == null) {
            UUID agentId = request.getAgent().getId();
            String title = ChatTitleUtil.truncate(request.getMessage(), 50);
            session = chatSessionService.create(agentId, title, tenantId, userId);
            sessionId = session.getId();
            log.info("chat new session sessionId={} userId={} tenantId={} agentId={}", sessionId, userId, tenantId, agentId);
        } else {
            sessionId = request.getSessionId();
            session = chatSessionService.getSession(sessionId, tenantId, userId);
            UUID effectiveAgentId = session.getAgentId();
            if (request.getAgent() != null && request.getAgent().getId() != null
                    && !request.getAgent().getId().equals(effectiveAgentId)) {
                log.info("chat ignore divergent agentId request={} session={} sessionId={}", request.getAgent().getId(), effectiveAgentId, sessionId);
            }
            log.info("chat existing session sessionId={} userId={} tenantId={} agentId={}", sessionId, userId, tenantId, effectiveAgentId);
        }

        UUID effectiveAgentId = session.getAgentId();
        Agent agent = agentService.findById(effectiveAgentId);
        if (agent == null) {
            throw new AgentNotFoundException();
        }

        AiProvider provider = agent.getProvider() != null ? agent.getProvider() : AiProvider.OLLAMA;
        String model = agent.getModel() != null && !agent.getModel().isBlank()
                ? agent.getModel()
                : VendorCatalog.defaultModelFor(provider);
        VendorCatalog.validate(provider, model);

        // Ponte credential->chat: resolve (tenant, vendor) -> apiKey/baseUrl; ausente => 422 CONFIG_MISSING.
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(tenantId, provider);

        // TX1: save user message
        chatMessageService.save(sessionId, tenantId, ChatRole.USER, request.getMessage());

        // Outside TX: history + provider call
        List<ChatMessage> history = chatMessageService.findLast20(sessionId, tenantId);
        // Converted once and reused: the RAG augmentation needs the conversation in the provider's own
        // message type (it is the query transformer's chat memory), and the prompt needs the same list.
        // Two conversions of the same history would be two chances to disagree about what was said.
        List<dev.langchain4j.data.message.ChatMessage> conversation = toProviderMessages(history);

        RagContext ragContext = resolveRagContext(tenantId, effectiveAgentId, sessionId, request.getMessage(),
                provider, model, credential, conversation);

        String reply;
        // Measured from here so elapsedMs isolates what the provider is asked to do: the prompt
        // assembly and the RAG resolution are already accounted for elsewhere — the RAG line carries
        // the search, the compressor carries its own — so a large value here means the provider stalled.
        long startedAt = System.nanoTime();
        List<dev.langchain4j.data.message.ChatMessage> prompt = buildMessages(agent, conversation, ragContext);
        try {
            ChatModel client = llmClientFactory.createClientWithSession(provider, model, agent.getTemperature(),
                    agent.getMaxTokens(), agent.getTopP(), credential.apiKey(), credential.baseUrl(), sessionId);
            dev.langchain4j.model.chat.response.ChatResponse full = retryPolicy.call(() -> client.chat(prompt));
            reply = textOf(full);
            if (reply == null || reply.isBlank()) {
                // Verified empty BEFORE persisting: chat_message.content is NOT NULL, so saving this
                // turned a provider quirk into a 409 data-integrity error that told the user nothing.
                // Reasoning models are the trigger — mimo-v2.5 with max_tokens=32 spends the whole
                // budget on reasoning and returns content=null; glm-5.2 returns "". Both are covered.
                recordTurn(sessionId, effectiveAgentId, provider, model, agent, history, prompt, ragContext,
                        elapsedMs(startedAt), EMPTY_MODEL_RESPONSE, full);
                log.warn("chat empty model response sessionId={} tenantId={} agentId={} provider={} model={} maxTokens={}",
                        sessionId, tenantId, effectiveAgentId, provider, model, agent.getMaxTokens());
                throw new ModelReturnedEmptyException(model);
            }
            recordTurn(sessionId, effectiveAgentId, provider, model, agent, history, prompt, ragContext,
                    elapsedMs(startedAt), null, full);
        } catch (Exception e) {
            if (e instanceof br.com.systec.hive.commons.api.exceptions.BaseException base) {
                throw base;
            }
            recordTurn(sessionId, effectiveAgentId, provider, model, agent, history, prompt, ragContext,
                    elapsedMs(startedAt), RagDiagnostic.causeOf(e), null);
            if (isModelNotFound(e)) {
                if (discoveryService != null) {
                    discoveryService.evict(provider);
                }
                log.warn("chat model unavailable sessionId={} tenantId={} agentId={} provider={} model={}",
                        sessionId, tenantId, effectiveAgentId, provider, model);
                throw new ModelUnavailableException(model, String.valueOf(provider));
            }
            log.error("provider failure sessionId={} tenantId={} agentId={}", sessionId, tenantId, effectiveAgentId, e);
            throw new ProviderException("LLM provider failure", e);
        }

        // TX2: save assistant + touch atomically
        var assistantMsg = chatAssistantTxService.saveAssistantAndTouch(sessionId, tenantId, reply);

        ChatResponse chatResponse = new ChatResponse();
        chatResponse.setSessionId(sessionId);
        chatResponse.setMessage(reply);
        chatResponse.setRole(ChatRole.ASSISTANT);
        chatResponse.setRagUsed(ragContext.used());
        chatResponse.setCitations(ragContext.citations());
        // Motivo do contexto ausente: sem isso a FE só vê ragUsed=false e o modelo inventa uma
        // explicação ("nao recebi contexto") sem que o usuario saiba a causa real.
        chatResponse.setRagReason(ragContext.outcome().wireName());
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
     * @param failureText already-classified failure label, or {@code null} for a healthy turn
     */
    private void recordTurn(UUID sessionId, UUID agentId, AiProvider provider, String model, Agent agent,
                            List<ChatMessage> history, List<dev.langchain4j.data.message.ChatMessage> prompt,
                            RagContext ragContext, long elapsedMs, String failureText,
                            dev.langchain4j.model.chat.response.ChatResponse full) {
        try {
            var usage = full == null ? null : full.tokenUsage();
            String finishReason = full == null || full.finishReason() == null ? null : full.finishReason().name();
            traceRecorder.record(ChatTrace.builder(sessionId, agentId, provider, model)
                    .agentLimits(agent.getMaxTokens(), agent.getTopP(), agent.getTemperature())
                    .prompt(history.size(), prompt.size(), promptChars(prompt))
                    .rag(ragContext.used(), ragContext.citations().size(), ragContext.outcome().wireName(),
                            ragContext.threshold())
                    .elapsedMs(elapsedMs)
                    .usage(usage == null ? null : usage.inputTokenCount(),
                            usage == null ? null : usage.outputTokenCount(),
                            usage == null ? null : usage.totalTokenCount(),
                            finishReason)
                    .failure(failureText)
                    .build());
        } catch (Exception e) {
            log.warn("chat trace recorder failed sessionId={}", sessionId, e);
        }
    }

    /**
     * Text of the provider reply, or {@code null} when there is none. A 200 with no
     * {@code aiMessage} and a 200 with an {@code aiMessage} whose text is {@code null} are the same
     * situation for the caller, and neither may reach the {@code content NOT NULL} column.
     */
    private static String textOf(dev.langchain4j.model.chat.response.ChatResponse full) {
        if (full == null || full.aiMessage() == null) {
            return null;
        }
        return full.aiMessage().text();
    }

    /**
     * Size of the assembled prompt. Only the length is ever recorded — prompt and answer text
     * must not reach the log.
     */
    private static int promptChars(List<dev.langchain4j.data.message.ChatMessage> messages) {
        int total = 0;
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            String text = textOf(message);
            if (text != null) {
                total += text.length();
            }
        }
        return total;
    }

    private static String textOf(dev.langchain4j.data.message.ChatMessage message) {
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

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    static boolean isModelNotFound(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return true;
            }
            if (current instanceof dev.langchain4j.exception.HttpException http) {
                if (http.statusCode() == 404) {
                    // 404 só é "modelo removido" quando o provider respondeu JSON de erro
                    // falando do modelo. 404 com corpo HTML significa rota inexistente
                    // (ex.: baseUrl sem /v1) — isso é erro de configuração, não modelo
                    // indisponível, e não pode virar 422 enganoso. O pattern genérico NÃO é
                    // aplicado nesse corpo, senão a landing page casaria com "model ... not found".
                    if (isProviderModelErrorPayload(http.getMessage())) {
                        return true;
                    }
                    current = current.getCause();
                    continue;
                }
            }
            String message = current.getMessage();
            if (message != null && MODEL_NOT_FOUND_PATTERN.matcher(message).find()) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * O corpo do {@code HttpException} é o payload bruto do provider. Só um JSON de erro que
     * cita o modelo indica modelo removido; qualquer outra coisa (HTML, vazio, JSON de rota)
     * é falha de configuração do endpoint.
     */
    private static boolean isProviderModelErrorPayload(String body) {
        if (body == null) {
            return false;
        }
        String payload = body.stripLeading();
        if (!payload.startsWith("{")) {
            return false;
        }
        return MODEL_NOT_FOUND_PATTERN.matcher(payload).find();
    }

    /**
     * The conversation in the provider's message type, dropping entries with no content.
     *
     * <p>System messages are kept in the list but the query transformer ignores them: a system prompt
     * is an instruction to the model, not something the user said, and a compressor that read it would
     * rewrite the question into an instruction.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> toProviderMessages(List<ChatMessage> history) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage msg : history) {
            String content = msg.getContent() != null ? msg.getContent() : msg.getMessage();
            if (content == null) {
                continue;
            }
            if (msg.getRole() == ChatRole.ASSISTANT) {
                messages.add(new AiMessage(content));
            } else if (msg.getRole() == ChatRole.SYSTEM) {
                messages.add(new SystemMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    private List<dev.langchain4j.data.message.ChatMessage> buildMessages(Agent agent,
                                                                          List<dev.langchain4j.data.message.ChatMessage> conversation,
                                                                          RagContext ragContext) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        String systemPrompt = "";
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            systemPrompt = agent.getSystemPrompt();
        } else if (agent.getPromptTemplate() != null && agent.getPromptTemplate().getTemplate() != null) {
            systemPrompt = agent.getPromptTemplate().getTemplate();
        } else if (agent.getPrompt() != null) {
            systemPrompt = agent.getPrompt();
        }
        if (!systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (ragContext.used()) {
            messages.add(ragContext.injected());
        }
        messages.addAll(conversation);
        return messages;
    }

    /**
     * Resolves the knowledge context for a turn and emits <b>exactly one</b> diagnostic for it,
     * whatever the path. Before this, {@code kbIds} empty, "base not in this tenant" and "nothing
     * above the threshold" all returned an empty context in silence, so a grep on the backend could
     * not tell them apart — the only visible symptom was the model claiming it had no context.
     *
     * <p>Two lines are emitted per turn that reaches the retrieval, and that is not redundancy: the
     * retrieval service also serves the playground, which has no agent, and it cannot report an
     * {@code agentId} it never sees. The rule that keeps the two honest is that the turn's line is
     * <b>derived</b> from the retrieval's result — see {@link RagRetrievalResult#toTurnDiagnostic} —
     * and adds only the two facts the retrieval is in no position to know: which agent asked, and
     * how big the context block was. The latency is the one field that legitimately differs: it is
     * measured at each level. This turn's number is the whole RAG resolution, so when the query
     * compressor fires it is included — which is the point, since that is the cost the operator is
     * trying to account for.
     *
     * <p>The context is assembled by the official pipeline
     * ({@link dev.langchain4j.rag.DefaultRetrievalAugmentor}: transformer → router → retriever →
     * aggregator → injector) rather than by a hand-built string. What leaves this method is the same
     * four things as before — was context used, which documents to cite, which outcome, which
     * cut-off — because the report is not what this change is about.
     */
    private RagContext resolveRagContext(UUID tenantId, UUID agentId, UUID sessionId, String question,
                                         AiProvider provider, String agentModel,
                                         CredentialResolver.ResolvedCredential credential,
                                         List<dev.langchain4j.data.message.ChatMessage> conversation) {
        long startedAt = System.nanoTime();
        if (kbRetrieval == null || kbLinkPort == null) {
            emitRag(RagDiagnostic.builder(RagOutcome.RAG_DISABLED, agentId, tenantId)
                    .reason("knowledge module not wired")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.RAG_DISABLED);
        }
        if (question == null || question.isBlank()) {
            emitRag(RagDiagnostic.builder(RagOutcome.NO_LINK, agentId, tenantId)
                    .reason("empty question")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.NO_LINK);
        }
        List<UUID> kbIds;
        try {
            kbIds = kbLinkPort.linkedKbIds(agentId, tenantId);
        } catch (Exception e) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .reason("agent->knowledge base link lookup failed")
                    .cause(e)
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }
        if (kbIds == null || kbIds.isEmpty()) {
            emitRag(RagDiagnostic.builder(RagOutcome.NO_LINK, agentId, tenantId)
                    .kbIds(List.of())
                    .reason("agent has no knowledge base linked")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.NO_LINK);
        }

        // The retrieval's own report is published by the retriever as it runs, because the official
        // interface cannot carry it. Reading it from here — rather than rebuilding it from the contents
        // the augmentor returns — is what keeps the outcome, the counts and the cut-off describing the
        // search that actually happened.
        AtomicReference<RagRetrievalResult> published = new AtomicReference<>();
        Function<dev.langchain4j.rag.query.Query, Integer> maxResults = query -> RAG_MAX_RESULTS;
        Function<dev.langchain4j.rag.query.Query, Double> minScore =
                query -> KbRetrievalPort.MIN_SCORE_FROM_KB_CONVENTION;
        KnowledgeBaseContentRetriever retriever = KnowledgeBaseContentRetriever.of(
                kbRetrieval, tenantId, kbIds, maxResults, minScore, published::set);
        RetrievalAugmentor augmentor = augmentorFactory.create(retriever,
                queryTransformer(provider, agentModel, credential));

        AugmentationResult augmentation;
        try {
            UserMessage userMessage = new UserMessage(question);
            augmentation = augmentor.augment(new AugmentationRequest(userMessage,
                    Metadata.from(userMessage, sessionId, conversationBefore(conversation, question))));
        } catch (KbDimensionMismatchException e) {
            // The thrown outcome never produces a result, so this line is built here. It has to carry
            // what the retrieval's own line carried — including how many bases were found, which is
            // on the exception precisely so a mismatch between two existing bases is not reported as
            // a lookup that found nothing. Reaches this catch unwrapped because the augmentor routes
            // one query to one retriever on the calling thread; a change to the routing (several
            // retrievers) would wrap it in a CompletionException and this line would stop firing,
            // which is why the IT below asserts the outcome rather than the absence of an exception.
            emitRag(RagDiagnostic.builder(RagOutcome.DIMENSION_MISMATCH, agentId, tenantId)
                    .kbIds(kbIds)
                    .foundKbs(e.foundKbs())
                    .reason("linked knowledge bases have different embedding dimensions")
                    .cause(e)
                    .build(), startedAt);
            throw e;
        } catch (Exception e) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .kbIds(kbIds)
                    .reason("knowledge retrieval failed")
                    .cause(e)
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }

        RagRetrievalResult retrieval = published.get();
        if (retrieval == null) {
            emitRag(RagDiagnostic.builder(RagOutcome.ERROR, agentId, tenantId)
                    .kbIds(kbIds)
                    .reason("retrieval port returned no result")
                    .build(), startedAt);
            return RagContext.empty(RagOutcome.ERROR);
        }
        // Both conditions, not one. A retrieval can report chunks and still inject nothing — a chunk
        // with no text cannot become a Content at all — and the augmentor answers an empty list by
        // returning the message it was given, which here is the question itself. Without this guard the
        // turn would add the question to the prompt a second time, claim ragUsed=true with no knowledge
        // behind it, and report the length of the question as the size of the context block.
        if (!retrieval.hasChunks() || augmentation.contents().isEmpty()) {
            emitRag(retrieval.toTurnDiagnostic(agentId, tenantId, kbIds, RagDiagnostic.NO_SYSTEM_BLOCK),
                    startedAt);
            return RagContext.empty(retrieval.outcome(), retrieval.threshold());
        }
        dev.langchain4j.data.message.ChatMessage injected = augmentation.chatMessage();
        String injectedText = textOf(injected);
        int injectedChars = injectedText == null ? 0 : injectedText.length();
        emitRag(retrieval.toTurnDiagnostic(agentId, tenantId, kbIds, injectedChars), startedAt);
        // Citations come from what was injected, not from what the search returned: a citation is a
        // claim to the user about where the answer came from, and it may not name a chunk the model
        // never saw. The two differ whenever a chunk is dropped for having no text.
        return RagContext.of(injected, injectedChars, citationsOf(augmentation.contents()),
                retrieval.outcome(), retrieval.threshold());
    }

    /**
     * The citations of the contents that were actually injected.
     *
     * <p>A content this pipeline produced is a {@link RetrievedContent}, and the identity is what makes
     * the mapping exact — the same object the model was given. A content of any other type is logged
     * and skipped rather than cited: a citation the client shows as a source has to point at something
     * in the prompt, and a content this class cannot attribute is not something it can claim.
     */
    private static List<KbCitation> citationsOf(List<dev.langchain4j.rag.content.Content> contents) {
        List<KbCitation> citations = new ArrayList<>(contents.size());
        for (dev.langchain4j.rag.content.Content content : contents) {
            if (content instanceof RetrievedContent retrieved) {
                KbCitation citation = new KbCitation();
                citation.setDocId(retrieved.docId());
                citation.setDocName(retrieved.docName());
                citation.setChunkId(retrieved.chunkId());
                citation.setScore(retrieved.score());
                citations.add(citation);
            } else {
                log.warn("rag content without a citation source type={} — the model saw it and the "
                        + "client cannot be told where it came from", content.getClass().getName());
            }
        }
        return List.copyOf(citations);
    }

    /**
     * The conversation the compressor is allowed to read: everything before the question being asked.
     *
     * <p>The turn's own message is already in the saved history — it is saved before the RAG
     * resolution — and the compressor's template takes the conversation and the new query as two
     * separate inputs. Leaving the current question in both means the model reads it twice and pays
     * for it twice, in the one stage whose cost this project is trying to keep down.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> conversationBefore(
            List<dev.langchain4j.data.message.ChatMessage> conversation, String question) {
        List<dev.langchain4j.data.message.ChatMessage> previous = new ArrayList<>(conversation);
        while (!previous.isEmpty()) {
            dev.langchain4j.data.message.ChatMessage last = previous.get(previous.size() - 1);
            if (last instanceof UserMessage user && user.singleText().equals(question)) {
                previous.remove(previous.size() - 1);
                continue;
            }
            break;
        }
        return List.copyOf(previous);
    }

    /**
     * The query compressor of this turn — always built, including when the stage is off.
     *
     * <p>Building it costs one object; skipping it would cost the operator the one thing they need
     * before switching the feature on, which is how often it <em>would</em> fire. With the stage off
     * the transformer still runs the heuristic and logs the verdict at {@code DEBUG}, so the fire rate
     * is observable without a single extra LLM call. "Off" is expressed by the supplier being absent,
     * and the supplier is only asked for after the heuristic has fired — a turn that needs no rewrite
     * never builds a model client.
     */
    private dev.langchain4j.rag.query.transformer.QueryTransformer queryTransformer(
            AiProvider provider, String agentModel, CredentialResolver.ResolvedCredential credential) {
        String model = queryCompressionModel == null || queryCompressionModel.isBlank()
                ? null
                : queryCompressionModel;
        return new ConditionalCompressingQueryTransformer(
                model == null ? null : () -> compressionModel(provider, agentModel, credential),
                model, ellipticalQuestionLimit);
    }

    /**
     * The cheap model the rewrite may be spent on, or {@code null} when it must not be spent at all.
     *
     * <p>Three refusals, each for a reason an operator can act on:
     * <ul>
     *   <li><b>same model as the agent</b> — the rewrite would run on the reasoning model the turn is
     *       already paying for, which is the worst case: whole conversation in, ~40 tokens out, plus
     *       the risk that a small {@code maxTokens} makes it return nothing (this project's
     *       {@code empty_model_response}). Naming a different model is the fix.</li>
     *   <li><b>not in the vendor's catalog</b> — the call would fail at request time; better to search
     *       with the original words than to fail the turn.</li>
     *   <li><b>no credential for the provider</b> — already resolved for the agent, so a failure here
     *       is a configuration problem worth a log line, not an exception.</li>
     * </ul>
     */
    private ChatModel compressionModel(AiProvider provider, String agentModel,
                                       CredentialResolver.ResolvedCredential credential) {
        if (agentModel != null && agentModel.equals(queryCompressionModel)) {
            log.warn("query compression disabled reason=same_model_as_agent model={} provider={}",
                    queryCompressionModel, provider);
            return null;
        }
        if (!VendorCatalog.supports(provider, queryCompressionModel)) {
            log.warn("query compression disabled reason=model_not_in_catalog model={} provider={}",
                    queryCompressionModel, provider);
            return null;
        }
        try {
            return llmClientFactory.createClient(provider, queryCompressionModel, 0.0,
                    COMPRESSION_MAX_TOKENS, null, credential.apiKey(), credential.baseUrl());
        } catch (Exception e) {
            log.warn("query compression disabled reason=client_unavailable model={} provider={} cause={}",
                    queryCompressionModel, provider, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Stamps the turn's own wall time on the line and hands it to the sink.
     *
     * <p>Unconditional, and that is the point: the retrieval's latency is already on its own line, so
     * a turn line reporting it back — or reporting {@code 0} because the search was fast enough to
     * measure as zero — is a number that describes neither scope.
     */
    private void emitRag(RagDiagnostic diagnostic, long startedAt) {
        try {
            ragRecorder.record(diagnostic.withElapsedMs(elapsedMs(startedAt)));
        } catch (Exception e) {
            log.warn("rag diagnostic recorder failed agentId={}", diagnostic.agentId(), e);
        }
    }

    /**
     * What the RAG resolution decided for this turn.
     *
     * @param used         whether any knowledge reached the prompt
     * @param citations    the sources, in the order the model received them (most relevant first) —
     *                     derived from the injected contents, never from the raw retrieval result
     * @param injected     the message the pipeline produced, carrying the retrieved knowledge
     * @param injectedChars its size, which is what the diagnostic reports as {@code systemBlockChars}
     * @param outcome      why the retrieval ended the way it did — unchanged by the pipeline move
     * @param threshold    the cut-off the search actually ran with, {@code null} if it resolved none
     */
    record RagContext(boolean used, List<KbCitation> citations,
                      dev.langchain4j.data.message.ChatMessage injected, int injectedChars,
                      RagOutcome outcome, Double threshold) {
        static RagContext empty(RagOutcome outcome) {
            return empty(outcome, null);
        }

        static RagContext empty(RagOutcome outcome, Double threshold) {
            return new RagContext(false, List.of(), null, 0, outcome, threshold);
        }

        static RagContext of(dev.langchain4j.data.message.ChatMessage injected, int injectedChars,
                             List<KbCitation> citations, RagOutcome outcome, Double threshold) {
            return new RagContext(true, citations, injected, injectedChars, outcome, threshold);
        }
    }
}
