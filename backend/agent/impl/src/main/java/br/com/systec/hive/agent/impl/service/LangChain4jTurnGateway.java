package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.InferenceParams;
import br.com.systec.hive.agent.api.domain.TurnReply;
import br.com.systec.hive.agent.api.domain.TurnRequest;
import br.com.systec.hive.agent.api.port.ChatTurnGateway;
import br.com.systec.hive.agent.api.util.ModelNotFound;
import br.com.systec.hive.agent.impl.util.ProviderMessages;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The LangChain4j-backed {@link ChatTurnGateway}: prompt assembly, the provider call, the retry
 * policy and the empty-reply guard.
 *
 * <p>It knows nothing about sessions, tenants or telemetry. It is handed a
 * {@link TurnRequest} and returns a {@link TurnReply} already measured in the shape the trace
 * needs — which is the whole reason a caller cannot get this wrong by asking for the wrong thing
 * here.
 *
 * <p>It does not throw. Every outcome, including a failed provider call, comes back as a
 * {@link TurnReply}, because the prompt measurements the caller has to report belong to this class
 * and a thrown exception would take them with it.
 *
 * <h3>The model-not-found rule no longer lives here</h3>
 *
 * <p>It used to, as a static method, because this was the layer that speaks HTTP: a {@code 404} means
 * "the model was removed" only when the body is a JSON error naming the model, and means "your
 * {@code baseUrl} is missing the {@code /v1}" when the body is an HTML landing page. The same status
 * code carries two opposite diagnoses, and the difference is in the transport's vocabulary — so the
 * rule is a transport rule, and putting it in the caller meant re-implementing the transport's
 * vocabulary one layer up.
 *
 * <p>It moved to {@link ModelNotFound}, in the {@code api} module, with the second caller of this port
 * in sight. The rule decides a status code and an operator-facing sentence, which makes it policy:
 * two copies would each be locally correct and would disagree silently, which is the one failure mode
 * a rule about an HTTP status code cannot be allowed to have. This class is the adapter that speaks
 * the transport; the verdict about what the transport said is now stated once, above it.
 *
 * <p>What stayed outside that class on purpose: the eviction of the cached vendor list, the log line
 * and the {@code 422}. Those name a session and a tenant, they are what an operator reads, and a rule
 * about HTTP status codes should not know that any of it exists.
 */
@Component
public class LangChain4jTurnGateway implements ChatTurnGateway {

    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final LlmRetryPolicy retryPolicy;

    public LangChain4jTurnGateway(LlmClientFactory<ChatModel> llmClientFactory) {
        this.llmClientFactory = llmClientFactory;
        this.retryPolicy = LlmRetryPolicy.defaults();
    }

    @Override
    public TurnReply execute(TurnRequest request) {
        // Outside the try, exactly as the prompt assembly always was: a defect here is a defect in
        // this class, and it must not be reported as a provider failure.
        InferenceParams params = request.promptTemplate().getInferenceParams() != null
                ? request.promptTemplate().getInferenceParams()
                : new InferenceParams();
        List<dev.langchain4j.data.message.ChatMessage> prompt = buildMessages(request);
        int promptChars = ProviderMessages.totalChars(prompt);

        dev.langchain4j.model.chat.response.ChatResponse chatResponse;
        try {
            // Gate R2 (public-api-chat): createClientWithSession does NOT tolerate a null sessionId
            // (LangChain4jClientFactory throws IllegalArgumentException; opencodeHeaders calls
            // sessionId.toString()). In the stateless public flow, TurnRequest.sessionId carries a
            // synthetic per-request UUID (the turn id of the response), while ChatTrace.sessionId
            // stays null. No null-coercion is performed here: a null propagates as the factory's
            // IllegalArgumentException, which is returned as a failed TurnReply.
            ChatModel client = llmClientFactory.createClientWithSession(request.provider(), request.model(),
                    params.getTemperature(), params.getMaxTokens(),
                    params.getTopP(), request.apiKey(), request.baseUrl(), request.sessionId());
            chatResponse = retryPolicy.call(() -> client.chat(prompt));
        } catch (Exception e) {
            // Returned with the prompt measurements intact, and carrying the provider's exception
            // as-is. A wrapper would change the root class name and the status code the caller's
            // classification reads — the difference between a trace saying `HttpException:not_found`
            // and one saying `TurnFailedException:...`.
            return TurnReply.failed(prompt.size(), promptChars, e);
        }

        String reply = ProviderMessages.textOf(chatResponse);
        TokenUsage usage = chatResponse == null ? null : chatResponse.tokenUsage();
        String finishReason = chatResponse == null || chatResponse.finishReason() == null ? null : chatResponse.finishReason().name();
        Integer promptTokens = usage == null ? null : usage.inputTokenCount();
        Integer completionTokens = usage == null ? null : usage.outputTokenCount();
        Integer totalTokens = usage == null ? null : usage.totalTokenCount();

        if (reply == null || reply.isBlank()) {
            // Returned, not thrown, for the same reason as the failure above: the caller has to write
            // a trace for this turn, and a trace for it needs `finishReason` and the token usage.
            // Reasoning models are the trigger: mimo-v2.5 with max_tokens=32 spends the whole budget
            // on reasoning and returns content=null; glm-5.2 returns "". Both are covered.
            return TurnReply.emptyResponse(prompt.size(), promptChars,
                    promptTokens, completionTokens, totalTokens, finishReason);
        }
        return TurnReply.answered(reply, prompt.size(), promptChars,
                promptTokens, completionTokens, totalTokens, finishReason);
    }

    /**
     * The system prompt this turn runs with: the conversation's own template, verbatim.
     *
     * <p>The agent used to sit between the two and pick one of three texts, in an order that had to
     * be read to be believed. There is now one source, and it is the thing the user picked when they
     * started the conversation. A template with blank text contributes nothing rather than an empty
     * {@code SystemMessage}, because an empty system message is an instruction to the model to be
     * brief.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> buildMessages(TurnRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        String template = request.promptTemplate().getTemplate();

        if (template != null && !template.isBlank()) {
            messages.add(new SystemMessage(template));
        }

        messages.addAll(request.conversation());
        return messages;
    }
}
