package br.com.systec.hive.publicapi.web.internal.v1.serializer;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionChunkDTO;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionUsage;
import br.com.systec.hive.publicapi.api.dto.OpenAiError;
import java.util.List;
import java.util.Locale;

/**
 * Pure {@code TurnStreamEvent → frame} functions for the OpenAI-compatible wire (ADR-002 §4.1).
 * Nothing here reads {@code Throwable.getMessage()}: the error frame carries the closed code only (F1).
 */
public final class PublicSseSerializer {

    private PublicSseSerializer() {
    }

    /** Request-scoped identity captured before the stream opens; no callback reads ambient context. */
    public record Meta(String id, long created, String model) {
    }

    public static ChatCompletionChunkDTO reasoning(Meta meta, TurnStreamEvent.Reasoning event) {
        return chunk(meta, ChatCompletionChunkDTO.Delta.reasoning(event.text()), null);
    }

    public static ChatCompletionChunkDTO text(Meta meta, TurnStreamEvent.Text event) {
        return chunk(meta, ChatCompletionChunkDTO.Delta.content(event.text()), null);
    }

    public static ChatCompletionChunkDTO finish(Meta meta, TurnStreamEvent.Done event) {
        return chunk(meta, new ChatCompletionChunkDTO.Delta(), finishReason(event));
    }

    /** Terminal usage chunk. {@code null} means the provider never reported usage — nothing is invented. */
    public static ChatCompletionChunkDTO usage(Meta meta, TurnStreamEvent.Done event) {
        Integer[] counts = TurnStreamEvent.tokenCounts(event);
        if (counts == null) {
            return null;
        }
        ChatCompletionUsage body = new ChatCompletionUsage(counts[0], counts[1], counts[2]);
        return new ChatCompletionChunkDTO(meta.id(), "chat.completion.chunk", meta.created(), meta.model(),
                List.of(), body);
    }

    public static OpenAiError error(TurnStreamEvent.Failed event) {
        String code = event.code() == null ? "stream_failed" : event.code();
        return new OpenAiError(new OpenAiError.Error(code, "server_error", null, code));
    }

    private static ChatCompletionChunkDTO chunk(Meta meta, ChatCompletionChunkDTO.Delta delta, String finishReason) {
        return new ChatCompletionChunkDTO(meta.id(), "chat.completion.chunk", meta.created(), meta.model(),
                List.of(new ChatCompletionChunkDTO.Choice(0, delta, finishReason)), null);
    }

    private static String finishReason(TurnStreamEvent.Done event) {
        String reason = TurnStreamEvent.finishReasonName(event);
        return reason == null ? "stop" : reason.toLowerCase(Locale.ROOT);
    }
}
