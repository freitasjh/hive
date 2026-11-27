package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.SessionGateRegistry;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** RF8 (ADR-001 D6/Q5): sync and streaming serialize on the same {@code SessionGateRegistry}. */
class SyncStreamSharedGateTest {

    @Test
    void when_streamHoldsGate_then_syncTurnStartsOnlyAfterTerminal() throws Exception {
        UUID session = UUID.randomUUID();
        AtomicReference<StreamingChatResponseHandler> provider = new AtomicReference<>();
        AtomicBoolean streamTerminal = new AtomicBoolean();
        LangChain4jTurnStreamGateway streamGateway = new LangChain4jTurnStreamGateway(
                streamingFactory(provider), text -> text == null || text.isBlank() ? AssistantTurnGateway.PROMPT : text, new StreamingMetrics(), scheduler());

        ChatModel model = mock(ChatModel.class);
        CountDownLatch syncEntered = new CountDownLatch(1);
        when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            assertThat(streamTerminal).as("sync must not overlap the streaming ChatMemory").isTrue();
            syncEntered.countDown();
            return ChatResponse.builder().aiMessage(AiMessage.from("answer"))
                    .finishReason(FinishReason.STOP).build();
        });
        AssistantTurnGateway syncGateway = gatewayWith(model);
        Semaphore gate = SessionGateRegistry.shared().gateFor(session);

        StreamingTurnHandle handle = streamGateway.stream(request(session),
                event -> { if (event instanceof TurnStreamEvent.Done) streamTerminal.set(true); });
        assertThat(gate.availablePermits()).isZero();

        Thread syncTurn = new Thread(() -> syncGateway.execute(request(session)));
        syncTurn.start();
        assertThat(syncEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();

        provider.get().onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("stream"))
                .finishReason(FinishReason.STOP).build());
        assertThat(syncEntered.await(2, TimeUnit.SECONDS)).isTrue();
        syncTurn.join(2_000);
        assertThat(gate.availablePermits()).isEqualTo(1);
        handle.cancel();
    }

    @Test
    void when_bothGatewaysStart_then_theyUseTheSameRegistryAndSemaphore() throws Exception {
        UUID session = UUID.randomUUID();
        LangChain4jTurnStreamGateway streamGateway = new LangChain4jTurnStreamGateway(
                streamingFactory(new AtomicReference<>()), text -> text == null || text.isBlank() ? AssistantTurnGateway.PROMPT : text, new StreamingMetrics(), scheduler());

        Field gates = LangChain4jTurnStreamGateway.class.getDeclaredField("gates");
        gates.setAccessible(true);
        Object registry = gates.get(streamGateway);
        assertThat(registry).isSameAs(SessionGateRegistry.shared());
        assertThat(SessionGateRegistry.shared().gateFor(session)).isSameAs(
                ((SessionGateRegistry) registry).gateFor(session));
    }

    @Test
    void when_syncAcquireIsInterrupted_then_failedReplyWithoutReleasingForeignPermit() {
        UUID session = UUID.randomUUID();
        Semaphore gate = SessionGateRegistry.shared().gateFor(session);
        ChatModel model = mock(ChatModel.class);
        AssistantTurnGateway gateway = gatewayWith(model);
        try {
            gate.acquire();
            Thread.currentThread().interrupt();
            TurnReply reply = gateway.execute(request(session));

            assertThat(Thread.interrupted()).isTrue(); // WF6: flag restored for the caller
            assertThat(reply.status()).isEqualTo(TurnReply.Status.FAILED);
            assertThat(reply.failure()).isInstanceOf(InterruptedException.class);
            assertThat(gate.availablePermits()).isZero(); // no permit released that was not acquired
            verifyNoInteractions(model);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } finally {
            Thread.interrupted();
            gate.release();
        }
    }

    private static AssistantTurnGateway gatewayWith(ChatModel model) {
        LlmClientFactory<ChatModel> factory = mock(LlmClientFactory.class);
        when(factory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(model);
        return new AssistantTurnGateway(factory);
    }

    private static TurnRequest request(UUID session) {
        return new TurnRequest(session, new TurnPrompt(UUID.randomUUID(), "system", 0.3, 128, 0.9),
                AiProvider.OPENAI, "model-x", "key", null,
                List.of(UserMessage.from("question")));
    }

    private static LlmStreamingClientFactory<StreamingChatModel> streamingFactory(
            AtomicReference<StreamingChatResponseHandler> provider) {
        return new LlmStreamingClientFactory<>() {
            @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return model(); }
            @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return model(); }

            private StreamingChatModel model() {
                return new StreamingChatModel() {
                    @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                        provider.set(handler);
                    }
                };
            }
        };
    }

    private static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }
}
