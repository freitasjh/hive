package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NFR-05 / F6: the four streaming counters are names and numbers, never text payloads. */
class StreamingMetricsContractIT {

    private static final String TOKEN = "CANARIO-METRICA-93d1";

    @Test
    void when_counterNamesComeFromGateway_then_closedSetHasNoContent() throws Exception {
        StreamingMetrics metrics = new StreamingMetrics();
        degradedCancel(metrics, UUID.randomUUID());
        failed(metrics, UUID.randomUUID(), new IllegalStateException(TOKEN));
        failed(metrics, UUID.randomUUID(), new java.util.concurrent.TimeoutException(TOKEN));
        supportedCancel(metrics, UUID.randomUUID());

        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.FAILED)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.TIMEOUT)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.CLIENT_ABORT)).isEqualTo(1);

        Field counters = StreamingMetrics.class.getDeclaredField("counters");
        counters.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, AtomicLong> values = (Map<String, AtomicLong>) counters.get(metrics);
        assertThat(values.keySet()).isEqualTo(Set.of(
                StreamingMetrics.CANCEL_UNSUPPORTED, StreamingMetrics.FAILED,
                StreamingMetrics.TIMEOUT, StreamingMetrics.CLIENT_ABORT));
        assertThat(values.keySet()).noneMatch(name -> name.contains(TOKEN));
        assertThatThrownBy(() -> metrics.increment(TOKEN)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void degradedCancel(StreamingMetrics metrics, UUID session) {
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(new AtomicReference<>(), h -> h.onPartialResponse("a")), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler());
        gateway.stream(StreamingITFixtures.request(session), event -> { }).cancel();
    }

    private static void failed(StreamingMetrics metrics, UUID session, Throwable failure) {
        new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(new AtomicReference<>(), h -> h.onError(failure)), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler())
                .stream(StreamingITFixtures.request(session), event -> { });
        Semaphore gate = StreamingITFixtures.gate(session);
        assertThat(gate.availablePermits()).isEqualTo(1); // Failed released exactly once
    }

    private static void supportedCancel(StreamingMetrics metrics, UUID session) {
        new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(new AtomicReference<>(), h ->
                        h.onPartialResponse(new dev.langchain4j.model.chat.response.PartialResponse("a"),
                                new dev.langchain4j.model.chat.response.PartialResponseContext(
                                        new dev.langchain4j.model.chat.response.StreamingHandle() {
                                            @Override public void cancel() { }
                                            @Override public boolean isCancelled() { return false; }
                                        }))), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler())
                .stream(StreamingITFixtures.request(session), event -> { }).cancel();
    }
}
