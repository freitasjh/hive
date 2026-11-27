package br.com.systec.hive.assistant.web.internal.v1.serializer;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure {@code TurnStreamEvent → named event} functions for the assistant wire (ADR-002 §4.2).
 * The controller adds the {@code event:} line; this class only decides the name and the JSON body.
 */
public final class AssistantSseSerializer {

    private AssistantSseSerializer() {
    }

    public record NamedEvent(String name, Object data) {
    }

    public static NamedEvent thinking(TurnStreamEvent.Reasoning event) {
        return of("thinking", "thinking", event.text());
    }

    public static NamedEvent text(TurnStreamEvent.Text event) {
        return of("text", "text", event.text());
    }

    public static NamedEvent done(TurnStreamEvent.Done event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "done");
        if (event.sessionId() != null) {
            data.put("sessionId", event.sessionId().toString());
        }
        String reason = TurnStreamEvent.finishReasonName(event);
        data.put("finish_reason", reason == null ? "stop" : reason.toLowerCase(Locale.ROOT));
        Integer[] counts = TurnStreamEvent.tokenCounts(event);
        if (counts != null) {
            Map<String, Object> usageBody = new LinkedHashMap<>();
            usageBody.put("prompt_tokens", counts[0]);
            usageBody.put("completion_tokens", counts[1]);
            usageBody.put("total_tokens", counts[2]);
            data.put("usage", usageBody);
        }
        return new NamedEvent("done", data);
    }

    /** Terminal emitted after an error frame when the provider never produced a Done. */
    public static NamedEvent doneWithoutUsage() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "done");
        data.put("finish_reason", "stop");
        return new NamedEvent("done", data);
    }

    public static NamedEvent error(TurnStreamEvent.Failed event) {
        String code = event.code() == null ? "stream_failed" : event.code();
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "error");
        data.put("error", error);
        return new NamedEvent("error", data);
    }

    private static NamedEvent of(String name, String type, String text) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("text", text);
        return new NamedEvent(name, data);
    }
}
