package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.RagContext;
import br.com.systec.hive.agent.api.domain.TurnReply;
import br.com.systec.hive.agent.api.domain.TurnRequest;
import br.com.systec.hive.agent.api.port.ChatTurnGateway;
import br.com.systec.hive.agent.impl.util.ProviderMessages;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The LangChain4j-backed {@link ChatTurnGateway}: prompt assembly, the provider call, the retry
 * policy, the empty-reply guard and the model-not-found rule.
 *
 * <p>It knows nothing about sessions, tenants, knowledge bases or telemetry. It is handed a
 * {@link TurnRequest} that already carries the RAG decision and returns a {@link TurnReply} already
 * measured in the shape the trace needs — which is the whole reason a caller cannot get this wrong
 * by asking for the wrong thing here.
 *
 * <p>It does not throw. Every outcome, including a failed provider call, comes back as a
 * {@link TurnReply}, because the prompt measurements the caller has to report belong to this class
 * and a thrown exception would take them with it.
 *
 * <h3>Why the model-not-found rule lives here and not in the caller</h3>
 *
 * <p>It is a statement about how providers speak HTTP, and this is the layer that speaks HTTP: a
 * {@code 404} means "the model was removed" only when the body is a JSON error naming the model,
 * and means "your {@code baseUrl} is missing the {@code /v1}" when the body is an HTML landing
 * page. The same status code carries two opposite diagnoses, and the difference is in the transport's
 * vocabulary — so the rule is a transport rule, and putting it in the caller meant re-implementing
 * the transport's vocabulary one layer up. See {@link #isModelNotFound}.
 *
 * <p>What stayed outside on purpose: the eviction of the cached vendor list, the two log lines and
 * the {@code 422}. Those name a session and a tenant, they are what an operator reads, and this
 * class sees neither.
 */
@Component
public class LangChain4jTurnGateway implements ChatTurnGateway {

    /**
     * Wording a provider uses to say the model is gone. Deliberately narrow: it is applied to
     * exception messages and to provider error bodies, and a loose pattern here turns a routing
     * problem into a "model removed" verdict.
     */
    static final Pattern MODEL_NOT_FOUND_PATTERN = Pattern.compile(
            "(?i)(model_not_found|no such model|unknown model|\\bmodel\\b.*(not found|does not exist))");

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
        List<dev.langchain4j.data.message.ChatMessage> prompt = buildMessages(request);
        int promptChars = ProviderMessages.totalChars(prompt);

        dev.langchain4j.model.chat.response.ChatResponse chatResponse;
        try {
            ChatModel client = llmClientFactory.createClientWithSession(request.provider(), request.model(),
                    request.agent().getTemperature(), request.agent().getMaxTokens(),
                    request.agent().getTopP(), request.apiKey(), request.baseUrl(), request.sessionId());
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
     * The system prompt this turn runs with: the agent's own, else its template's, else its prompt.
     *
     * <p>Order matters and is the agent's existing precedence; an agent with no system prompt at all
     * contributes nothing, because an empty {@code SystemMessage} is an instruction to the model to
     * be brief.
     */
    private static List<dev.langchain4j.data.message.ChatMessage> buildMessages(TurnRequest request) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        String systemPrompt = "";
        var agent = request.agent();

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

        RagContext ragContext = request.ragContext();
        if (ragContext.used()) {
            messages.add(ragContext.injected());
        }
        messages.addAll(request.conversation());
        return messages;
    }

    /**
     * Whether this failure means "the provider removed the model", as opposed to every other reason
     * a provider call can fail.
     *
     * <p>The whole chain is walked, because a timeout arrives wrapped twice by the time the caller
     * sees it. A {@code 404} is the interesting case and it is <b>not</b> enough on its own: only a
     * JSON error body that mentions the model counts. A {@code 404} carrying an HTML page is a route
     * that does not exist — a {@code baseUrl} without {@code /v1} — which is a configuration error
     * and must not become a misleading {@code 422 "model unavailable"}. The generic pattern is
     * therefore not applied to an HTML body: an opencode landing page contains the words
     * "model ... not found" in its navigation and would match.
     */
    static boolean isModelNotFound(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof dev.langchain4j.exception.ModelNotFoundException) {
                return true;
            }
            if (current instanceof dev.langchain4j.exception.HttpException http) {
                if (http.statusCode() == 404) {
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
     * The body of the {@code HttpException} is the provider's raw payload. Only a JSON error that
     * names the model indicates a removed model; anything else (HTML, empty, a JSON about the route)
     * is an endpoint configuration failure.
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
}
