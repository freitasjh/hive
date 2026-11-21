package br.com.systec.hive.ai.api;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.UUID;

/**
 * The AiService contract of one generation turn (ADR-020 §5.1, moved to {@code ai/api} by ADR-021).
 *
 * <p>This interface is the generic shape of what the model receives and returns. It deliberately
 * carries <b>no default system text</b>: the fallback is assistant policy and lives in
 * {@code assistant/impl}. The caller resolves precedence and passes the final system string in
 * {@code TurnPrompt.text}; the proxy never overrides it.
 *
 * <h3>Why {@code Result<String>} and not {@code String}</h3>
 *
 * <p>ADR-020 §5.2 requires {@code TurnReply} to carry measured {@code TokenUsage}/{@code FinishReason}.
 * {@code Result<T>} is the LangChain4j 1.19.0 type that returns them together with the content
 * ({@code Result.tokenUsage()}, {@code Result.finishReason()}, {@code Result.finalResponse()});
 * verified in the 1.19.0 sources, tag 1.19.0, 2026-11-21. A model listener would not be "the result
 * of the proxy" that RF-06 asks for.
 *
 * <h3>Verified API adjustment (2026-11-21)</h3>
 *
 * <p>The initial ADR draft put {@code @SystemMessage} on the {@code system} parameter. In jar 1.19.0
 * the annotation has {@code @Target({TYPE, METHOD})} — a parameter does not compile. The text goes
 * on the method and the {@code {{system}}} variable is bound by {@code @V("system")} on the
 * parameter ({@code DefaultAiServices.prepareSystemMessage}, lines 606-613). Precedence over
 * {@code systemMessageProvider} is confirmed there too (lines 615-637).
 *
 * <p>{@code @UserMessage} went on the method for the same reason: annotated on a parameter,
 * LangChain4j uses the argument value as the template
 * ({@code findUserMessageTemplateFromAnnotatedParameter}, lines 739-749), so a user text containing
 * {@code {{...}}} would be interpreted as a variable and an empty text would throw
 * {@code IllegalConfigurationException}. On the method the template is constant and the value
 * arrives through {@code @V}, with no unintended interpolation.
 *
 * <h3>{@code @MemoryId}</h3>
 *
 * <p>{@code sessionId} — never {@code turnId}. UUID has correct {@code equals}/{@code hashCode},
 * so it is eligible as a memory key (MemoryId.java, tag 1.19.0, 2026-11-21).
 */
public interface AssistantDefault {

    /**
     * @param sessionId memory key per session — stable across turns, never the turn id
     * @param system    system text for this turn, already resolved by the assistant policy
     * @param user      this turn's text; prior turns come from the provider's {@code ChatMemory}
     */
    @SystemMessage("{{system}}")
    @UserMessage("{{user}}")
    Result<String> chat(@MemoryId UUID sessionId,
                        @V("system") String system,
                        @V("user") String user);
}
