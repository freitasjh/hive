package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** NS-11 — thinking never reaches a log, span, trace field or JPA column; only the payload carries it. */
class ChatSpanContractStreamingTest {

    private static final String THINKING_TOKEN = "THINK-UNICO-7f31c9";
    private static final String SECRET = "SEGREDO-DO-DRIVER-9b12";

    @Test
    void when_thinkingAndSecretFailure_then_thinkingOnlyInReasoningAndSecretOnlyInCause() {
        List<TurnStreamEvent> events = new ArrayList<>();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingTestFixtures.factory(provider, handler -> {
                    handler.onPartialThinking(new PartialThinking(THINKING_TOKEN));
                    handler.onPartialResponse("answer");
                    handler.onError(new IllegalStateException("driver failed with " + SECRET));
                }), StreamingTestFixtures.policy(),
                new StreamingMetrics(), StreamingTestFixtures.scheduler());
        StreamingTurnHandle ignore = gateway.stream(StreamingTestFixtures.request(UUID.randomUUID()), events::add);

        assertThat(events).hasSize(3);
        assertThat(((TurnStreamEvent.Reasoning) events.get(0)).text()).isEqualTo(THINKING_TOKEN);
        TurnStreamEvent.Failed failed = (TurnStreamEvent.Failed) events.get(2);
        assertThat(failed.code()).doesNotContain(SECRET, THINKING_TOKEN);
        assertThat(failed.cause()).isNotNull(); // in-memory only; never rendered by this module
    }

    @Test
    void when_scanMainSources_then_noThrowableMessageOrPrintStackTraceOnSsePath() throws IOException {
        List<Path> hits;
        try (Stream<Path> paths = Files.walk(StreamingTestFixtures.repoRoot().resolve("backend"))) {
            hits = paths.filter(Files::isRegularFile)
                    .filter(p -> (p.toString().contains("/ai/") || p.toString().contains("/provider/"))
                            && p.toString().contains("/src/main/") && p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return source.contains(".getMessage()") || source.contains("printStackTrace()")
                                    || source.contains("System.out.");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList();
        }
        assertThat(hits).as("F1: no exception text or stdout on the SSE path").isEmpty();
    }

    @Test
    void when_reasoningModelCompletes_then_doneHasNoThinkingText() {
        List<TurnStreamEvent> events = new ArrayList<>();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        new LangChain4jTurnStreamGateway(StreamingTestFixtures.factory(provider, handler -> {
            handler.onPartialThinking(new PartialThinking(THINKING_TOKEN));
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("answer"))
                    .finishReason(FinishReason.STOP).build());
        }), StreamingTestFixtures.policy(), new StreamingMetrics(), StreamingTestFixtures.scheduler())
                .stream(StreamingTestFixtures.request(UUID.randomUUID()), events::add);

        TurnStreamEvent.Done done = (TurnStreamEvent.Done) events.getLast();
        assertThat(done.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(done.toString()).doesNotContain(THINKING_TOKEN);
    }
}
