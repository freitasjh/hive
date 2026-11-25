package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.assistant.api.service.TurnOutcomeReporter;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.assistant.api.exceptions.ModelUnavailableException;
import br.com.systec.hive.assistant.api.exceptions.ProviderException;
import br.com.systec.hive.assistant.api.util.ModelNotFound;
import br.com.systec.hive.assistant.impl.diagnostics.ChatTurnSpanScope;
import br.com.systec.hive.assistant.impl.diagnostics.FailureClassifier;
import br.com.systec.hive.assistant.impl.diagnostics.SpanningChatTraceRecorder;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * What a turn's outcome means: the trace line, the operator line, and the throwable the caller raises.
 *
 * <p>The turn gateway returns every outcome — including a failed provider call — as a value, because
 * the measurements a trace needs belong to the call that produced them. Somebody still has to turn
 * those three values into three different consequences, and that somebody is this class:
 *
 * <ul>
 *   <li>{@link TurnReply.Status#EMPTY_RESPONSE} — a trace labelled {@code empty_model_response}, a
 *       {@code warn} naming the model and its ceiling, and a 422;</li>
 *   <li>{@link TurnReply.Status#FAILED} — the provider's own exception when it is already one of this
 *       project's, otherwise a classified trace, and then either a vendor-cache eviction with a 422
 *       (the model is gone) or a 502 (everything else);</li>
 *   <li>{@link TurnReply.Status#ANSWERED} — a trace with no failure, and nothing else.</li>
 * </ul>
 *
 * <h3>What the caller does with the return value</h3>
 *
 * <p>There is no return value: {@link #resolve} raises. The three branches end exactly where they
 * always did — with the exception the caller would have thrown — and the answered branch is the only
 * one that returns. The caller therefore reads as "settle this turn, and if it settled, persist the
 * reply", which is the whole contract: a turn that must not persist an assistant row never reaches
 * TX2. Keeping the {@code throw} inside the branch is also what keeps the three branches byte for
 * byte what they were on the caller, classification and all.
 *
 * <p>The fourth branch is closed on purpose. The two cases above decide by throwing, so arriving at
 * the answered branch with either one means the enum grew a member this class does not know about —
 * or an alternative implementation of the port returned {@code null}. Falling through on that would
 * reach the save with a null text, and {@code chat_message.content} is NOT NULL: a wiring mistake
 * would surface as the 409 data-integrity error this flow exists to prevent, about a turn nobody
 * diagnosed.
 *
 * <h3>What moved here, and what did not</h3>
 *
 * <p>The three branches came out of {@link ChatServiceImpl} as they were: the same classification,
 * the same trace label, the same two log lines, the same 422-vs-502 decision, and the same order. The
 * {@link #recordTurn} they all share came with them, because a trace is the record of an outcome and
 * the outcome is what this class decides.
 *
 * <p>The log lines moved with the branches, so their logger is now this class rather than the caller.
 * The message, the level and the fields are unchanged; only the emitting class differs, which is the
 * unavoidable cost of moving a branch instead of copying it.
 *
 * <p>What stayed in the caller: the ordering of the two transactions, the session handling, the rate
 * limit and the measurement of elapsed time. Those are about a turn's place in a conversation, and
 * this class sees a turn in isolation.
 */
@Service
public class TurnOutcomeMapper implements TurnOutcomeReporter {

    /**
     * Failure label written to the trace when the provider answered 200 with no usable text. It is a
     * literal rather than a {@code Throwable} because {@link FailureClassifier} classifies from
     * a closed set of exception codes and would degrade it to {@code ...:unexpected}, hiding the one
     * fact the operator needs: the model returned nothing on purpose, it did not crash.
     */
    static final String EMPTY_MODEL_RESPONSE = "empty_model_response";

    private static final Logger log = LoggerFactory.getLogger(TurnOutcomeMapper.class);

    private final ChatTraceRecorder traceRecorder;
    private final OpencodeDiscoveryService discoveryService;

    public TurnOutcomeMapper(ChatTraceRecorder traceRecorder, OpencodeDiscoveryService discoveryService) {
        this.traceRecorder = new SpanningChatTraceRecorder(traceRecorder);
        this.discoveryService = discoveryService;
    }

    /**
     * Decide what this turn's outcome is, record its trace, and raise when the turn must not persist
     * an assistant message.
     *
     * <p>Returns only when the turn was answered, which is what lets the caller continue straight into
     * TX2 without testing a flag it would have to remember to check.
     *
     * @param sessionId        the conversation this turn belongs to
     * @param tenantId         tenant the conversation belongs to; it names the operator's line
     * @param promptTemplateId template the conversation runs with
     * @param provider         vendor that was asked
     * @param model            model that was asked
     * @param promptTemplate   template, for the generation ceilings the trace reports
     * @param history          messages replayed from history, for its size
     * @param reply            what the generation produced, measured
     * @param elapsedMs        wall time of the provider generation call — a large value here means the
     *                         provider stalled, which is exactly the signal a chat timeout needs
     * @param turnId           public turn id; {@code null} for the human chat flow (ADR-002/K6)
     * @param clientId         authenticated public client id; {@code null} for the human chat flow
     */
    @Override
    public void resolve(UUID sessionId, UUID tenantId, UUID promptTemplateId, AiProvider provider,
                        String model, PromptTemplate promptTemplate, List<ChatMessage> history,
                        TurnReply reply, long elapsedMs, UUID turnId, String clientId) {
        if (reply.status() == TurnReply.Status.EMPTY_RESPONSE) {
            // Verified empty BEFORE persisting: chat_message.content is NOT NULL, so saving this
            // turned a provider quirk into a 409 data-integrity error that told the user nothing.
            // Reasoning models are the trigger — mimo-v2.5 with max_tokens=32 spends the whole
            // budget on reasoning and returns content=null; glm-5.2 returns "". Both are covered.
            recordTurn(sessionId, tenantId, promptTemplateId, provider, model, promptTemplate, history,
                    reply, elapsedMs, EMPTY_MODEL_RESPONSE, turnId, clientId);
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
                // Este ramo não chama record: fecha o root aqui para não deixar trace órfão.
                ChatTurnSpanScope.closeIfOpen();
                throw base;
            }
            recordTurn(sessionId, tenantId, promptTemplateId, provider, model, promptTemplate, history,
                    reply, elapsedMs, FailureClassifier.causeOf(failure), turnId, clientId);
            if (ModelNotFound.isModelNotFound(failure)) {
                if (discoveryService != null) {
                    discoveryService.evict(provider);
                }
                log.warn("chat model unavailable sessionId={} tenantId={} promptTemplateId={} provider={} model={}",
                        sessionId, tenantId, promptTemplateId, provider, model);
                throw new ModelUnavailableException(model, String.valueOf(provider));
            }
            log.error("provider failure sessionId={} tenantId={} promptTemplateId={}", sessionId, tenantId,
                    promptTemplateId, failure);
            throw new ProviderException("LLM provider failure", failure);
        }

        // Closed on purpose. The two cases above decide by throwing, so arriving here with either one
        // means the enum grew a member this method does not know about — or an alternative
        // implementation of the port returned null. Falling through on that would reach the save
        // with a null text, and `chat_message.content` is NOT NULL: a wiring mistake would surface as
        // the 409 data-integrity error this class exists to prevent, about a turn nobody diagnosed.
        if (reply.status() != TurnReply.Status.ANSWERED) {
            ChatTurnSpanScope.closeIfOpen();
            throw new IllegalStateException("unreachable turn status: " + reply.status());
        }

        recordTurn(sessionId, tenantId, promptTemplateId, provider, model, promptTemplate, history,
                reply, elapsedMs, null, turnId, clientId);
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
     * @param turnId      public turn id, or {@code null} in the human flow
     * @param clientId    authenticated public client, or {@code null} in the human flow
     */
    private void recordTurn(UUID sessionId, UUID tenantId, UUID promptTemplateId, AiProvider provider,
                            String model, PromptTemplate promptTemplate, List<ChatMessage> history,
                            TurnReply reply, long elapsedMs, String failureText,
                            UUID turnId, String clientId) {
        try {
            // A linha de trace não tem campo de retrieval: o produto não recupera nada, e um campo
            // que não pode ser verdadeiro ensina o operador a ler um vocabulário que não descreve mais
            // o sistema. Ver o javadoc de ChatTrace.
            traceRecorder.record(ChatTrace.builder(sessionId, promptTemplateId, provider, model)
                    .tenantId(tenantId)
                    .turnId(turnId)
                    .clientId(clientId)
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
}
