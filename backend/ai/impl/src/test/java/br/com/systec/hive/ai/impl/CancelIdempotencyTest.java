package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** GW-05 — new streaming port beside an unchanged synchronous gateway. */
class CancelIdempotencyTest {

    @Test
    void when_streamWithValidRequest_then_handleReturnedAndEventsReachSink() {
        StreamingTurnHandle handle = new LangChain4jTurnStreamGateway(new FakeFactory(), StreamingTestFixtures.policy(),
                new StreamingMetrics(), new ScheduledThreadPoolExecutor(1))
                .stream(request(), sink -> { });

        assertThat(handle).isNotNull();
    }

    @Test
    void when_streamDrivesCallbacks_then_typedEventsReachSink() {
        List<TurnStreamEvent> events = new ArrayList<>();
        new LangChain4jTurnStreamGateway(new FakeFactory(), StreamingTestFixtures.policy(), new StreamingMetrics(),
                new ScheduledThreadPoolExecutor(1)).stream(request(), events::add);

        assertThat(events).as("events=%s", events).extracting(Object::getClass).containsExactly(
                TurnStreamEvent.Reasoning.class, TurnStreamEvent.Text.class, TurnStreamEvent.Done.class);
    }

    @Test
    void when_transportCancelsTwice_then_providerCancelOnceAndGateReleasedOnce() {
        Semaphore gate = new SessionGateRegistry().gateFor(UUID.randomUUID());
        assertThatCode(() -> gate.acquire()).doesNotThrowAnyException();
        AtomicInteger providerCancels = new AtomicInteger();
        StreamingHandle providerHandle = new StreamingHandle() {
            @Override public void cancel() { providerCancels.incrementAndGet(); }
            @Override public boolean isCancelled() { return true; }
        };
        StreamingTurnHandleImpl handle = new StreamingTurnHandleImpl(event -> { }, gate::release,
                new StreamingMetrics(), new ScheduledThreadPoolExecutor(1), 5_000L);
        handle.providerHandle(providerHandle);

        assertThatCode(() -> { handle.cancel(); handle.cancel(); }).doesNotThrowAnyException();
        assertThat(gate.availablePermits()).as("supported cancel releases at once (S24)").isEqualTo(1);
        handle.emit(new TurnStreamEvent.Done(FinishReason.STOP, null));

        assertThat(providerCancels).hasValue(1);
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void when_inspectChatTurnGateway_then_singleExecuteMethodRemains() throws Exception {
        Method[] methods = ChatTurnGateway.class.getDeclaredMethods();

        assertThat(methods).hasSize(1);
        assertThat(methods[0].getName()).isEqualTo("execute");
        assertThat(TurnStreamGateway.class.getDeclaredMethods()).hasSize(1);
        assertThat(StreamingTurnHandle.class.getDeclaredMethods()).hasSize(1);
    }

    private static TurnRequest request() {
        return new TurnRequest(UUID.randomUUID(), new TurnPrompt(UUID.randomUUID(), "system", 0.3, 128, 0.9),
                AiProvider.OPENAI, "model-x", "key", null,
                List.of(dev.langchain4j.data.message.UserMessage.from("question")));
    }

    private static final class FakeFactory implements LlmStreamingClientFactory<StreamingChatModel> {
        @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                Double topP, String key, String url) { return model(); }
        @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t, Integer max,
                Double topP, String key, String url) { return model(); }

        private static StreamingChatModel model() {
            return new StreamingChatModel() {
                @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    handler.onPartialThinking(new dev.langchain4j.model.chat.response.PartialThinking("p"));
                    handler.onPartialResponse(new PartialResponse("a"),
                            new PartialResponseContext(new StreamingHandle() {
                                @Override public void cancel() { }
                                @Override public boolean isCancelled() { return false; }
                            }));
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("a"))
                            .finishReason(FinishReason.STOP).build());
                }
            };
        }
    }
}
