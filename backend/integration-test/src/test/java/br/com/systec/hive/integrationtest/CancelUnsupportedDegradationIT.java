package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** ADR-001 D7'/Emenda 1 (F3): degraded cancel mutes the sink, holds the gate, counts a label only. */
class CancelUnsupportedDegradationIT {

    @Test
    void when_degradedCancel_then_noFrameNoFailedGateHeldAndCountedWithoutContent() {
        UUID session = UUID.randomUUID();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        List<TurnStreamEvent> events = new ArrayList<>();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                // no PartialResponseContext => CancellationUnsupportedStreamingHandle (S6)
                StreamingITFixtures.factory(provider, handler -> handler.onPartialResponse("a")), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler());
        StreamingTurnHandle handle = gateway.stream(StreamingITFixtures.request(session), events::add);
        Semaphore gate = StreamingITFixtures.gate(session);
        int before = events.size();

        assertThatCode(handle::cancel).doesNotThrowAnyException();

        assertThat(events.subList(before, events.size())).isEmpty();  // no Failed, no frame after cancel
        assertThat(gate.availablePermits()).isZero();                 // (d) gate still held
        assertThat(gate.tryAcquire()).isFalse();                      // (f) no second turn enters
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1);
        provider.get().onPartialResponse("late-token");
        assertThat(events).hasSize(before);                           // sink muted
    }

    @Test
    void when_degradedCancelThenNaturalTerminal_then_gateReleasedExactlyOnce() {
        UUID session = UUID.randomUUID();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(provider, handler -> handler.onPartialResponse("a")), StreamingITFixtures.policy(),
                new StreamingMetrics(), StreamingITFixtures.scheduler());
        StreamingTurnHandle handle = gateway.stream(StreamingITFixtures.request(session), event -> { });
        Semaphore gate = StreamingITFixtures.gate(session);

        handle.cancel();
        assertThat(gate.availablePermits()).isZero();
        provider.get().onCompleteResponse(StreamingITFixtures.response("answer"));
        assertThat(gate.availablePermits()).isEqualTo(1);

        gate.acquireUninterruptibly();
        provider.get().onCompleteResponse(StreamingITFixtures.response("again"));
        assertThat(gate.availablePermits()).isZero(); // a second terminal cannot double-release
        gate.release();
    }

    @Test
    void when_degradedCancelWithoutTerminal_then_frozenWindowReleasesGate() throws Exception {
        UUID session = UUID.randomUUID();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(new AtomicReference<>(),
                        handler -> handler.onPartialResponse("a")), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler());
        StreamingTurnHandle handle = gateway.stream(StreamingITFixtures.request(session), event -> { });
        Semaphore gate = StreamingITFixtures.gate(session);

        handle.cancel();
        assertThat(gate.availablePermits()).isZero();
        assertThat(gate.tryAcquire(1, 6, TimeUnit.SECONDS))
                .as("frozen 5_000 ms release window").isTrue();
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1);
    }

    @Test
    void when_providerSupportsCancel_then_providerHandleCancelledOnceAndClientAbortCounted() {
        UUID session = UUID.randomUUID();
        AtomicInteger providerCancels = new AtomicInteger();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(provider, handler ->
                        handler.onPartialResponse(new PartialResponse("a"), new PartialResponseContext(
                                new StreamingHandle() {
                                    @Override public void cancel() { providerCancels.incrementAndGet(); }
                                    @Override public boolean isCancelled() { return false; }
                                }))), StreamingITFixtures.policy(),
                metrics, StreamingITFixtures.scheduler());
        StreamingTurnHandle handle = gateway.stream(StreamingITFixtures.request(session), event -> { });
        Semaphore gate = StreamingITFixtures.gate(session);

        handle.cancel();
        handle.cancel();

        assertThat(providerCancels).hasValue(1);
        assertThat(metrics.get(StreamingMetrics.CLIENT_ABORT)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isZero();
        // Emenda 2 / S24: supported cancel releases now; a suppressed terminal cannot be awaited.
        assertThat(gate.availablePermits()).isEqualTo(1);
        provider.get().onCompleteResponse(StreamingITFixtures.response("late"));
        assertThat(gate.availablePermits()).isEqualTo(1);
    }
}
