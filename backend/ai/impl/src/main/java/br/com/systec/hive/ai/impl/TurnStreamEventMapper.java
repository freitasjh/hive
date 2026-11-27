package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.service.TokenStream;

import java.util.function.Consumer;

/**
 * B1 mapper (ADR-001 D3): attaches one typed callback per event kind, preserving arrival order.
 * No raw event parsing, no heuristic over {@code content}, no exception text in a frame.
 */
public final class TurnStreamEventMapper {

    private final Consumer<TurnStreamEvent> sink;

    private TurnStreamEventMapper(Consumer<TurnStreamEvent> sink) {
        this.sink = sink;
    }

    /** Factory name keeps the converter a class, not a static utility bag. */
    public static TurnStreamEventMapper of(Consumer<TurnStreamEvent> sink) {
        return new TurnStreamEventMapper(sink);
    }

    /** Plain callbacks; a provider that never emits thinking produces no Reasoning. */
    public void attachTo(TokenStream stream) {
        stream.onPartialResponse(text -> emitText(text));
        attachTerminalCallbacks(stream);
    }

    /**
     * Context-aware variant for the gateway: the context callback is the one place the provider's
     * {@link dev.langchain4j.model.chat.response.StreamingHandle} can be captured for disconnect.
     * LangChain4j forbids registering both partial-response overloads on one TokenStream.
     */
    public void attachTo(TokenStream stream, Consumer<PartialResponseContext> onContext) {
        stream.onPartialResponseWithContext((partial, context) -> {
            onContext.accept(context);
            emitText(partial == null ? null : partial.text());
        });
        attachTerminalCallbacks(stream);
    }

    private void emitText(String text) {
        if (text != null && !text.isEmpty()) {
            sink.accept(new TurnStreamEvent.Text(text));
        }
    }

    private void attachTerminalCallbacks(TokenStream stream) {
        stream.onPartialThinking(thinking -> {
            String text = thinking == null ? null : thinking.text();
            if (text != null && !text.isEmpty()) {
                sink.accept(new TurnStreamEvent.Reasoning(text));
            }
        });
        stream.onCompleteResponse(response -> sink.accept(new TurnStreamEvent.Done(
                response == null ? null : response.finishReason(),
                response == null ? null : response.tokenUsage())));
        stream.onError(cause -> sink.accept(new TurnStreamEvent.Failed(StreamFailureCodes.of(cause), cause)));
    }
}
