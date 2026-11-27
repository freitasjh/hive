package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MAP-03 — one typed event per callback, in callback arrival order (ADR-001 D3 / Q4).
 * Written before {@link TurnStreamEventMapper} existed.
 */
class TurnStreamEventMapperTest {

    private static final List<String> CLOSED_CODES = List.of("timeout", "cancelled", "rate_limited", "unauthorized",
            "model_not_found", "not_found", "server_error", "http_500", "connection_refused", "unknown_host",
            "socket_error", "sql_error", "unexpected");

    @Test
    void when_fourCallbacksArrive_then_fourTypedEventsInArrivalOrder() {
        FakeTokenStream stream = new FakeTokenStream();
        List<TurnStreamEvent> sink = new ArrayList<>();
        TurnStreamEventMapper.of(sink::add).attachTo(stream);

        stream.thinking("p");
        stream.text("a");
        stream.complete(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("a"))
                .finishReason(FinishReason.STOP).tokenUsage(new TokenUsage(12, 34, 46)).build());
        RuntimeException secret = new RuntimeException("SEGREDO-DO-DRIVER");
        stream.error(secret);

        assertThat(sink).hasSize(4);
        assertThat(sink.get(0)).isEqualTo(new TurnStreamEvent.Reasoning("p"));
        assertThat(sink.get(1)).isEqualTo(new TurnStreamEvent.Text("a"));
        assertThat(sink.get(2)).isInstanceOf(TurnStreamEvent.Done.class);
        assertThat(sink.get(3)).isInstanceOf(TurnStreamEvent.Failed.class);

        TurnStreamEvent.Failed failed = (TurnStreamEvent.Failed) sink.get(3);
        assertThat(CLOSED_CODES).contains(failed.code());
        assertThat(failed.code()).doesNotContain("SEGREDO");
        assertThat(failed.cause()).isSameAs(secret);
    }

    @Test
    void when_providerEmitsNoThinking_then_noReasoningEvent() {
        FakeTokenStream stream = new FakeTokenStream();
        List<TurnStreamEvent> sink = new ArrayList<>();
        TurnStreamEventMapper.of(sink::add).attachTo(stream);

        stream.text("a");
        stream.complete(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("a")).build());

        assertThat(sink).hasSize(2).noneMatch(e -> e instanceof TurnStreamEvent.Reasoning);
    }

    @Test
    void when_emptyThinkingAndText_then_eventsAreDiscarded() {
        FakeTokenStream stream = new FakeTokenStream();
        List<TurnStreamEvent> sink = new ArrayList<>();
        TurnStreamEventMapper.of(sink::add).attachTo(stream);

        stream.thinkingText(""); // PartialThinking's constructor rejects empty; simulate S3 violation via override
        stream.text("");
        stream.complete(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("a")).build());

        assertThat(sink).hasSize(1).allMatch(e -> e instanceof TurnStreamEvent.Done);
    }

    /** Minimal TokenStream: the callbacks under test are the only ones that do anything. */
    private static final class FakeTokenStream implements TokenStream {
        private Consumer<String> partial;
        private Consumer<PartialThinking> thinking;
        private Consumer<ChatResponse> complete;
        private Consumer<Throwable> error;

        void thinking(String text) { thinking.accept(new PartialThinking(text)); }
        void thinkingText(String text) {
            thinking.accept(new PartialThinking("non-empty") {
                @Override public String text() { return text; }
            });
        }
        void text(String text) { partial.accept(text); }
        void complete(ChatResponse response) { complete.accept(response); }
        void error(Throwable cause) { error.accept(cause); }

        @Override public TokenStream onPartialResponse(Consumer<String> c) { partial = c; return this; }
        @Override public TokenStream onPartialThinking(Consumer<PartialThinking> c) { thinking = c; return this; }
        @Override public TokenStream onCompleteResponse(Consumer<ChatResponse> c) { complete = c; return this; }
        @Override public TokenStream onError(Consumer<Throwable> c) { error = c; return this; }
        @Override public TokenStream ignoreErrors() { return this; }
        @Override public TokenStream onRetrieved(java.util.function.Consumer<List<dev.langchain4j.rag.content.Content>> c) { return this; }
        @Override public TokenStream onToolExecuted(Consumer<ToolExecution> c) { return this; }
        @Override public void start() { }
    }
}
