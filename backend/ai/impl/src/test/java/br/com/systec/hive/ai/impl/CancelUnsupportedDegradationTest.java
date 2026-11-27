package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CNC-07 / Emenda 1 (security F3): a degraded cancel mutes the sink but keeps the session gate until
 * the provider terminal or the short window. IT-equivalent in {@code ai/impl} because the
 * integration-test module is outside this ADR's write scope.
 */
class CancelUnsupportedDegradationTest {

    private final UUID session = UUID.randomUUID();
    private static final long TEST_WINDOW_MS = 40L;

    @Test
    void when_degradedCancel_then_noExceptionNoFailedNoFrameAndGateHeld() {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        List<TurnStreamEvent> events = new ArrayList<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, false, new AtomicInteger()), StreamingTestFixtures.policy(), metrics, scheduler(), TEST_WINDOW_MS);
        StreamingTurnHandle handle = gateway.stream(request(), events::add);
        Semaphore gate = gateOf(gateway);

        int before = events.size();
        assertThatCode(handle::cancel).doesNotThrowAnyException();

        assertThat(events.subList(before, events.size())).isEmpty();  // (b)(c) no Failed, no frame
        assertThat(gate.availablePermits()).isEqualTo(0);             // (d) gate still held
        assertThat(gate.tryAcquire()).isFalse();                      // (f) no second turn enters
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1); // (g) count only
        assertThat(events).noneMatch(e -> e.toString().contains("SEGREDO"));

        provider.get().onPartialResponse("late-SEGREDO");
        assertThat(events).hasSize(before);
    }

    @Test
    void when_degradedCancelThenNaturalTerminal_then_gateReleasedOnce() throws Exception {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, false, new AtomicInteger()), StreamingTestFixtures.policy(), new StreamingMetrics(),
                scheduler(), TEST_WINDOW_MS);
        StreamingTurnHandle handle = gateway.stream(request(), event -> { });
        Semaphore gate = gateOf(gateway);

        handle.cancel();
        assertThat(gate.availablePermits()).isEqualTo(0);
        provider.get().onCompleteResponse(response());
        assertThat(gate.availablePermits()).isEqualTo(1);

        gate.acquire();
        provider.get().onCompleteResponse(response());
        assertThat(gate.availablePermits()).isEqualTo(0); // second terminal cannot double-release
        gate.release();
    }

    @Test
    void when_degradedCancelWithoutTerminal_then_windowReleasesGateOnce() throws Exception {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, false, new AtomicInteger()), StreamingTestFixtures.policy(), metrics, scheduler(), TEST_WINDOW_MS);
        StreamingTurnHandle handle = gateway.stream(request(), event -> { });
        Semaphore gate = gateOf(gateway);

        handle.cancel();
        assertThat(gate.availablePermits()).isEqualTo(0);
        assertThat(gate.tryAcquire(1, TimeUnit.SECONDS)).as("short release window").isTrue();
        assertThat(gate.availablePermits()).isZero(); // tryAcquire consumed the single permit
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isEqualTo(1);
    }

    @Test
    void when_supportedCancel_then_providerHandleCancelledOnceAndGateReleasedImmediately() {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        AtomicInteger providerCancels = new AtomicInteger();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, true, providerCancels), StreamingTestFixtures.policy(), metrics, scheduler(), TEST_WINDOW_MS);
        StreamingTurnHandle handle = gateway.stream(request(), event -> { });
        Semaphore gate = gateOf(gateway);

        handle.cancel();
        handle.cancel();

        assertThat(providerCancels).hasValue(1);
        assertThat(metrics.get(StreamingMetrics.CLIENT_ABORT)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isZero();
        // S24: langchain4j suppresses the terminal after a supported cancel; the gate cannot wait for it.
        assertThat(gate.availablePermits()).isEqualTo(1);
        provider.get().onCompleteResponse(response()); // late terminal must not double-release
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void when_terminalSinkThrows_then_gateIsReleasedByFinally() {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, true, new AtomicInteger()), StreamingTestFixtures.policy(), metrics, scheduler(), TEST_WINDOW_MS);
        Semaphore gate = gateOf(gateway);
        gateway.stream(request(), event -> {
            if (event instanceof TurnStreamEvent.Done) {
                throw new IllegalStateException("transport failed");
            }
        });
        assertThat(gate.availablePermits()).isZero();

        assertThatThrownBy(() -> provider.get().onCompleteResponse(response()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(gate.availablePermits()).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.FAILED)).isZero();
    }

    @Test
    void when_cancelWithoutProviderHandle_then_clientAbortCountedAndWindowReleases() throws Exception {
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        StreamingMetrics metrics = new StreamingMetrics();
        LangChain4jTurnStreamGateway gateway = new LangChain4jTurnStreamGateway(
                new FakeFactory(provider, false, new AtomicInteger(), false), StreamingTestFixtures.policy(), metrics, scheduler(), TEST_WINDOW_MS);
        StreamingTurnHandle handle = gateway.stream(request(), event -> { });
        Semaphore gate = gateOf(gateway);

        handle.cancel();

        assertThat(metrics.get(StreamingMetrics.CLIENT_ABORT)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isZero();
        assertThat(gate.availablePermits()).isZero();
        assertThat(gate.tryAcquire(1, TimeUnit.SECONDS)).isTrue();
    }

    private static ChatResponse response() {
        return ChatResponse.builder().aiMessage(AiMessage.from("a")).finishReason(FinishReason.STOP).build();
    }

    private TurnRequest request() {
        return new TurnRequest(session, new TurnPrompt(UUID.randomUUID(), "system", 0.3, 128, 0.9),
                AiProvider.OPENAI, "model-x", "key", null, List.of(UserMessage.from("question")));
    }

    private Semaphore gateOf(LangChain4jTurnStreamGateway gateway) {
        try {
            Field field = LangChain4jTurnStreamGateway.class.getDeclaredField("gates");
            field.setAccessible(true);
            return ((SessionGateRegistry) field.get(gateway)).gateFor(session);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static final class FakeFactory implements LlmStreamingClientFactory<StreamingChatModel> {
        private final AtomicReference<StreamingChatResponseHandler> provider;
        private final boolean withContext;
        private final AtomicInteger providerCancels;
        private final boolean emitPartial;

        FakeFactory(AtomicReference<StreamingChatResponseHandler> provider, boolean withContext,
                AtomicInteger providerCancels) {
            this(provider, withContext, providerCancels, true);
        }

        FakeFactory(AtomicReference<StreamingChatResponseHandler> provider, boolean withContext,
                AtomicInteger providerCancels, boolean emitPartial) {
            this.provider = provider;
            this.withContext = withContext;
            this.providerCancels = providerCancels;
            this.emitPartial = emitPartial;
        }

        @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                Double topP, String key, String url) { return model(); }
        @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t, Integer max,
                Double topP, String key, String url) { return model(); }

        private StreamingChatModel model() {
            return new StreamingChatModel() {
                @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    provider.set(handler);
                    if (!emitPartial) {
                        return; // no callback at all: no provider handle to cancel
                    }
                    if (withContext) {
                        handler.onPartialResponse(new PartialResponse("a"), new PartialResponseContext(
                                new StreamingHandle() {
                                    @Override public void cancel() { providerCancels.incrementAndGet(); }
                                    @Override public boolean isCancelled() { return false; }
                                }));
                    } else {
                        handler.onPartialResponse("a"); // no context => CancellationUnsupportedStreamingHandle
                    }
                }
            };
        }
    }
}
