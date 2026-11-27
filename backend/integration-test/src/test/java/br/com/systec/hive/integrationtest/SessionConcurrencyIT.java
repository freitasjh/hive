package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** RF8 (ADR-001 §5): one fair semaphore per session for the sync and streaming turn gateways. */
class SessionConcurrencyIT {

    @Test
    void when_streamHoldsGate_then_syncTurnStartsOnlyAfterStreamingTerminal() throws Exception {
        UUID session = UUID.randomUUID();
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        AtomicBoolean streamTerminal = new AtomicBoolean();
        LangChain4jTurnStreamGateway streamGateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(provider, handler -> { }), StreamingITFixtures.policy(), new StreamingMetrics(),
                StreamingITFixtures.scheduler());

        ChatModel model = mock(ChatModel.class);
        CountDownLatch syncEntered = new CountDownLatch(1);
        when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            assertThat(streamTerminal).as("sync must not overlap the streaming ChatMemory").isTrue();
            syncEntered.countDown();
            return StreamingITFixtures.response("sync-answer");
        });

        streamGateway.stream(StreamingITFixtures.request(session), event -> {
            if (event instanceof br.com.systec.hive.ai.api.domain.TurnStreamEvent.Done) {
                streamTerminal.set(true);
            }
        });
        Semaphore gate = StreamingITFixtures.gate(session);
        assertThat(gate.availablePermits()).isZero();

        Thread syncTurn = new Thread(() -> StreamingITFixtures.syncGateway(model)
                .execute(StreamingITFixtures.request(session)));
        syncTurn.start();
        assertThat(syncEntered.await(100, TimeUnit.MILLISECONDS)).isFalse();

        provider.get().onCompleteResponse(StreamingITFixtures.response("stream-answer"));
        assertThat(syncEntered.await(2, TimeUnit.SECONDS)).isTrue();
        syncTurn.join(2_000);
        assertThat(syncTurn.isAlive()).isFalse();
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void when_syncHoldsGate_then_streamProviderStartsOnlyAfterSyncTerminal() throws Exception {
        UUID session = UUID.randomUUID();
        CountDownLatch providerStarted = new CountDownLatch(1);
        AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                new AtomicReference<>();
        LangChain4jTurnStreamGateway streamGateway = new LangChain4jTurnStreamGateway(
                StreamingITFixtures.factory(provider, handler -> providerStarted.countDown()), StreamingITFixtures.policy(),
                new StreamingMetrics(), StreamingITFixtures.scheduler());

        ChatModel model = mock(ChatModel.class);
        CountDownLatch syncRelease = new CountDownLatch(1);
        CountDownLatch syncEntered = new CountDownLatch(1);
        when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            syncEntered.countDown();
            assertThat(syncRelease.await(2, TimeUnit.SECONDS)).isTrue();
            return StreamingITFixtures.response("sync-answer");
        });
        Thread syncTurn = new Thread(() -> StreamingITFixtures.syncGateway(model)
                .execute(StreamingITFixtures.request(session)));
        syncTurn.start();
        assertThat(syncEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread streamTurn = new Thread(() ->
                streamGateway.stream(StreamingITFixtures.request(session), event -> { }));
        streamTurn.start();
        assertThat(providerStarted.await(100, TimeUnit.MILLISECONDS)).isFalse();

        syncRelease.countDown();
        syncTurn.join(2_000);
        assertThat(syncTurn.isAlive()).isFalse();
        assertThat(providerStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(StreamingITFixtures.gate(session).availablePermits()).isZero();
        provider.get().onCompleteResponse(StreamingITFixtures.response("stream-answer"));
    }

    @Test
    void when_sameSessionIsRequestedByBothGateways_then_gateIsTheSameFairSemaphore() {
        UUID session = UUID.randomUUID();
        Semaphore first = StreamingITFixtures.gate(session);
        Semaphore second = StreamingITFixtures.gate(session);

        assertThat(first).isSameAs(second);
        assertThat(first.isFair()).isTrue();
    }
}
