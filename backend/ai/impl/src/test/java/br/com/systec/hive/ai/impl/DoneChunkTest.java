package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** DON-09 — terminal Done carries finishReason and the 1.19.0 usage fields, nothing invented. */
class DoneChunkTest {

    @Test
    void when_completeResponseHasUsage_then_doneCarriesFinishReasonAndThreeCounts() {
        List<TurnStreamEvent> sink = new ArrayList<>();
        FakeTokenStream stream = new FakeTokenStream();
        TokenUsage usage = new TokenUsage(12, 34, 46);
        TurnStreamEventMapper.of(sink::add).attachTo(stream);

        stream.complete(ChatResponse.builder().aiMessage(AiMessage.from("a"))
                .finishReason(FinishReason.STOP).tokenUsage(usage).build());

        assertThat(sink).singleElement().isEqualTo(new TurnStreamEvent.Done(FinishReason.STOP, usage));
        TurnStreamEvent.Done done = (TurnStreamEvent.Done) sink.getFirst();
        assertThat(done.usage().inputTokenCount()).isEqualTo(12);
        assertThat(done.usage().outputTokenCount()).isEqualTo(34);
        assertThat(done.usage().totalTokenCount()).isEqualTo(46);
    }

    @Test
    void when_completeResponseHasNoUsage_then_doneStillEmittedWithNullUsage() {
        List<TurnStreamEvent> sink = new ArrayList<>();
        FakeTokenStream stream = new FakeTokenStream();
        TurnStreamEventMapper.of(sink::add).attachTo(stream);

        stream.complete(ChatResponse.builder().aiMessage(AiMessage.from("a"))
                .finishReason(FinishReason.LENGTH).build());

        TurnStreamEvent.Done done = (TurnStreamEvent.Done) sink.getFirst();
        assertThat(done.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(done.usage()).isNull();
    }

    @Test
    void when_scanBackendSources_then_noReasoningTokensFieldInvented() throws IOException {
        try (Stream<Path> paths = Files.walk(repoRoot().resolve("backend"))) {
            List<Path> hits = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("reasoning_tokens");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).toList();
            assertThat(hits).as("TokenUsage 1.19.0 has no reasoning_tokens (S4)").isEmpty();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of(".").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        assertThat(current).as("repo root with .git").isNotNull();
        return current;
    }

    private static final class FakeTokenStream implements TokenStream {
        private Consumer<ChatResponse> complete;
        void complete(ChatResponse response) { complete.accept(response); }
        @Override public TokenStream onCompleteResponse(Consumer<ChatResponse> c) { complete = c; return this; }
        @Override public TokenStream onPartialResponse(Consumer<String> c) { return this; }
        @Override public TokenStream onPartialThinking(Consumer<dev.langchain4j.model.chat.response.PartialThinking> c) { return this; }
        @Override public TokenStream onError(Consumer<Throwable> c) { return this; }
        @Override public TokenStream ignoreErrors() { return this; }
        @Override public TokenStream onRetrieved(Consumer<List<dev.langchain4j.rag.content.Content>> c) { return this; }
        @Override public TokenStream onToolExecuted(Consumer<ToolExecution> c) { return this; }
        @Override public void start() { }
    }
}
