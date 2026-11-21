package br.com.systec.hive.ai.api.domain;

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
 * <p>The caller renders {@code conversation} <b>once</b> and shares it: the prompt is built from it.
 * Rendering it twice would be two chances to disagree about what was said, and this project's
 * diagnosis lines have already been wrong once for being reconstructed rather than carried. The
 * persisted conversation stays in JPA; this record carries only its provider-type rendering, as
 * ADR-020 requires.
 *
 * <h3>Null policy — and it is shared with {@link TurnReply}</h3>
 *
 * <p>One rule across the records, because different rules in adjacent records is how a reader stops
 * trusting any of them:
 * <ul>
 *   <li><b>A collection is defaulted</b> — {@code null} and empty are the same fact, and coercing
 *       costs nothing.</li>
 *   <li><b>A value that carries meaning is required.</b> The prompt is required: a turn without one
 *       has no system text and no generation limits, and saying so at construction beats a prompt
 *       that quietly went out with neither.</li>
 * </ul>
 *
 * @param sessionId     session being answered, for the provider's session-scoped client
 * @param promptTemplate the {@link TurnPrompt} chosen for this turn — a pure copy of the persisted
 *                       template made by the caller, so {@code ai/api} never sees the template entity
 * @param provider      effective vendor
 * @param model         effective model name
 * @param apiKey        resolved credential — never rendered, see {@link #toString()}
 * @param baseUrl       resolved endpoint, {@code null} for the vendor default
 * @param conversation  the conversation in the provider's message type, never {@code null}
 */
public record TurnRequest(UUID sessionId, TurnPrompt promptTemplate, AiProvider provider, String model,
                          String apiKey, String baseUrl, List<ChatMessage> conversation) {

    public TurnRequest {
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        // Required, not defaulted. See the null policy above.
        promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
    }

    /**
     * Never renders the credential, and never renders a single character of the conversation.
     *
     * <p>Both would otherwise happen by accident. The record's generated {@code toString()} lists every
     * component, and this object crosses an API boundary where the credential used to be a
     * parameter that died in a call frame. One {@code log.debug(request)} in a future change, one
     * {@code MDC.put}, one framework dumping a request object, and the key is in a log file — which
     * cannot be un-leaked. The conversation is the user's own words, and a failed assertion that prints
     * this record would put them in a build log.
     *
     * <p>What is left is enough to diagnose a turn: which template, which vendor, which model, which
     * session, and how big the conversation was.
     */
    @Override
    public String toString() {
        return "TurnRequest[sessionId=" + sessionId
                + ", promptTemplateId=" + (promptTemplate == null ? null : promptTemplate.templateId())
                + ", provider=" + provider
                + ", model=" + model
                + ", baseUrl=" + baseUrl
                + ", apiKey=***"
                + ", conversation=" + conversation.size() + " message(s)"
                + "]";
    }
}
