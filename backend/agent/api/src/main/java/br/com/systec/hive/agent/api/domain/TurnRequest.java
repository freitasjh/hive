package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.provider.api.AiProvider;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything one turn's generation needs, and nothing about how it is reported.
 *
 * <p>This is what replaces two method signatures that had grown past the point of being readable: an
 * eight-argument prompt builder and an eleven-argument telemetry recorder, both of which had to be
 * read against the call site to know which of six same-typed arguments was which. Here the arguments
 * have names, and a new one is a compile error at every construction.
 *
 * <p>Two fields look redundant and are not. {@code history} is the conversation as it was persisted;
 * {@code conversation} is the same conversation rendered in the provider's message type. They are
 * rendered <b>once</b> by the caller and shared, because the RAG query transformer reads
 * {@code conversation} and the prompt is built from it — rendering it twice would be two chances to
 * disagree about what was said, and this project's diagnosis lines have already been wrong once for
 * being reconstructed rather than carried.
 *
 * <h3>Null policy — and it is shared with {@link RagContext} and {@link TurnReply}</h3>
 *
 * <p>One rule across the three records, because three different rules in three adjacent records is
 * how a reader stops trusting any of them:
 * <ul>
 *   <li><b>A collection is defaulted</b> — {@code null} and empty are the same fact, and coercing
 *       costs nothing.</li>
 *   <li><b>A value that carries meaning is required.</b> A null {@code ragContext} used to be read as
 *       {@code RAG_DISABLED} and reported to the client as a turn with no knowledge — a lie told
 *       about a broken wiring. It is now a construction failure with a stack trace pointing at the
 *       line that forgot it.</li>
 * </ul>
 *
 * @param sessionId   session being answered, for the provider's session-scoped client
 * @param agent       the agent, carrying its system prompt, template and generation limits
 * @param provider    effective vendor
 * @param model       effective model name
 * @param apiKey      resolved credential — never rendered, see {@link #toString()}
 * @param baseUrl     resolved endpoint, {@code null} for the vendor default
 * @param history     persisted conversation, counted on the trace as {@code historySize}
 * @param conversation the same conversation in the provider's message type, never {@code null}
 * @param ragContext  what the RAG resolution decided. An empty context is a context that resolved to
 *                    nothing, and conflating the two is how a turn that skipped retrieval starts
 *                    claiming it had none
 */
public record TurnRequest(UUID sessionId, Agent agent, AiProvider provider, String model,
                          String apiKey, String baseUrl,
                          List<br.com.systec.hive.agent.api.domain.ChatMessage> history,
                          List<ChatMessage> conversation,
                          RagContext ragContext) {

    public TurnRequest {
        history = history == null ? List.of() : List.copyOf(history);
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        // Required, not defaulted. See the null policy above.
        agent = Objects.requireNonNull(agent, "agent");
        ragContext = Objects.requireNonNull(ragContext, "ragContext");
    }

    /**
     * Never renders the credential, and never renders a single character of the conversation.
     *
     * <p>Both would otherwise happen by accident. The record's generated {@code toString()} lists every
     * component, and this object now crosses an API boundary where the credential used to be a
     * parameter that died in a call frame. One {@code log.debug(request)} in a future change, one
     * {@code MDC.put}, one framework dumping a request object, and the key is in a log file — which
     * cannot be un-leaked. The conversation is the user's own words, and a failed assertion that prints
     * this record would put them in a build log.
     *
     * <p>What is left is enough to diagnose a turn: which agent, which vendor, which model, which
     * session, and how big the two conversations were.
     */
    @Override
    public String toString() {
        return "TurnRequest[sessionId=" + sessionId
                + ", agentId=" + (agent == null ? null : agent.getId())
                + ", provider=" + provider
                + ", model=" + model
                + ", baseUrl=" + baseUrl
                + ", apiKey=***"
                + ", history=" + history.size() + " message(s)"
                + ", conversation=" + conversation.size() + " message(s)"
                + ", ragUsed=" + ragContext.used()
                + ", ragOutcome=" + ragContext.outcome()
                + ", ragCitations=" + ragContext.citations().size()
                + "]";
    }
}
