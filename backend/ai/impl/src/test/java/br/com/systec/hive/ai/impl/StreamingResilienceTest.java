package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** NR-12 — 120 s ceiling, bounded retries, no lost permit, content-free counters. */
class StreamingResilienceTest {

    @Test
    void when_tenDegradedCancels_then_counterTenAndNoExtraPermitRelease() throws Exception {
        UUID sessionId = UUID.randomUUID();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingTestFixtures.factory(provider, handler -> handler.onPartialResponse("a")), StreamingTestFixtures.policy(),
                metrics, StreamingTestFixtures.scheduler(), 40L);
        for (int i = 0; i < 10; i++) {
            StreamingTurnHandle handle = gateway.stream(StreamingTestFixtures.request(sessionId), event -> { });
            // AT-01: a finished turn evicts its gate; look up the live one for this turn.
            Semaphore gate = gateOf(gateway, sessionId);
            handle.cancel();
            assertThat(gate.availablePermits()).isZero();
            provider.get().onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("a")).build());
            assertThat(gate.availablePermits()).isEqualTo(1); // one permit for the next turn
        }

        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(10);
    }

    @Test
    void when_terminalFailed_then_permitIsReleased() {
        UUID sessionId = UUID.randomUUID();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingTestFixtures.factory(provider, handler ->
                        handler.onError(new dev.langchain4j.exception.HttpException(500, "down"))), StreamingTestFixtures.policy(),
                metrics, StreamingTestFixtures.scheduler());
        Semaphore gate = gateOf(gateway, sessionId);

        gateway.stream(StreamingTestFixtures.request(sessionId), event -> { });

        assertThat(gate.availablePermits()).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.FAILED)).isEqualTo(1);
    }

    @Test
    void when_retryLimitExhausted_then_attemptsStayBounded() {
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();
        LlmStreamingClientFactory<dev.langchain4j.model.chat.StreamingChatModel> counting =
                new LlmStreamingClientFactory<>() {
                    @Override public dev.langchain4j.model.chat.StreamingChatModel createClient(
                            br.com.systec.hive.provider.api.AiProvider p, String m, Double t, Integer max,
                            Double topP, String key, String url) { return client(); }
                    @Override public dev.langchain4j.model.chat.StreamingChatModel createStreamingClient(
                            br.com.systec.hive.provider.api.AiProvider p, String m, Double t, Integer max,
                            Double topP, String key, String url) { return client(); }
                    private dev.langchain4j.model.chat.StreamingChatModel client() {
                        starts.incrementAndGet();
                        return StreamingTestFixtures.factory(provider, handler ->
                                handler.onError(new dev.langchain4j.exception.HttpException(503, "busy")))
                                .createStreamingClient(br.com.systec.hive.provider.api.AiProvider.OPENAI,
                                        "m", null, null, null, "k", null);
                    }
                };

        new LangChain4jTurnStreamGateway(counting, StreamingTestFixtures.policy(), new StreamingMetrics(), StreamingTestFixtures.scheduler())
                .stream(StreamingTestFixtures.request(UUID.randomUUID()), event -> { });

        assertThat(starts).hasValue(StreamingRetryPolicy.defaults().maxAttempts());
    }

    @Test
    void when_scanBackend_then_noPkillAndTimeoutIs120s() throws IOException {
        try (Stream<Path> paths = Files.walk(StreamingTestFixtures.repoRoot().resolve("backend"))) {
            assertThat(paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("pkill -f");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList()).isEmpty();
        }
        assertThat(StreamingRetryPolicy.TURN_TIMEOUT.toSeconds()).isEqualTo(120L);
    }

    private static Semaphore gateOf(LangChain4jTurnStreamGateway gateway, UUID sessionId) {
        try {
            Field field = LangChain4jTurnStreamGateway.class.getDeclaredField("gates");
            field.setAccessible(true);
            return ((SessionGateRegistry) field.get(gateway)).gateFor(sessionId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
