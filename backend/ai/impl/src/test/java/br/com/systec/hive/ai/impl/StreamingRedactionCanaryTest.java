package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-16 / F1 — a unique thinking token and a secret-bearing Throwable must not surface as a frame
 * code or counter. The full log/ChatTrace/OTLP/JPA canary lives in integration-test (out of scope);
 * this one covers the in-module path that would feed those destinations.
 */
class StreamingRedactionCanaryTest {

    private static final String TOKEN = "CANARIO-THINKING-4d77e1";
    private static final String SECRET = "SENHA-DO-DRIVER-aa91";

    @Test
    void when_turnFailsWithSecretThrowable_then_failedCodeIsClosedLabel() {
        List<TurnStreamEvent> events = new ArrayList<>();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        new LangChain4jTurnStreamGateway(StreamingTestFixtures.factory(provider, handler -> {
            handler.onPartialThinking(new dev.langchain4j.model.chat.response.PartialThinking(TOKEN));
            handler.onError(new IllegalStateException("driver says " + SECRET));
        }), StreamingTestFixtures.policy(), new StreamingMetrics(), StreamingTestFixtures.scheduler())
                .stream(StreamingTestFixtures.request(UUID.randomUUID()), events::add);

        TurnStreamEvent.Failed failed = (TurnStreamEvent.Failed) events.getLast();
        assertThat(failed.code()).isEqualTo("unexpected").doesNotContain(TOKEN, SECRET);
        assertThat(events).noneMatch(e -> e instanceof TurnStreamEvent.Text);
    }

    @Test
    void when_counterIncrements_then_noThinkingOrSecretCanBeRecorded() {
        StreamingMetrics metrics = new StreamingMetrics();
        metrics.increment(StreamingMetrics.CANCEL_UNSUPPORTED);
        metrics.increment(StreamingMetrics.FAILED);
        metrics.increment(StreamingMetrics.TIMEOUT);

        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.FAILED)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.TIMEOUT)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> metrics.increment(TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> metrics.increment(SECRET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_gatewayEmitsFailed_then_causeIsNotSerializedIntoAnyOtherEvent() {
        List<TurnStreamEvent> events = new ArrayList<>();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider = new AtomicReference<>();
        new LangChain4jTurnStreamGateway(StreamingTestFixtures.factory(provider, handler ->
                handler.onError(new IllegalStateException(SECRET))), StreamingTestFixtures.policy(), new StreamingMetrics(),
                StreamingTestFixtures.scheduler()).stream(StreamingTestFixtures.request(UUID.randomUUID()), events::add);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(TurnStreamEvent.Failed.class);
        assertThat(((TurnStreamEvent.Failed) events.getFirst()).cause().getClass().getSimpleName())
                .isEqualTo("IllegalStateException");
    }
}
