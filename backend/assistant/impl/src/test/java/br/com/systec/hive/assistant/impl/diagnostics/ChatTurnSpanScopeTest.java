package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ChatTurnSpanScopeTest {

    private final List<List<ChatSpan>> published = new ArrayList<>();
    private final ChatSpanSink sink = published::add;

    private static ChatTrace trace(UUID session, UUID tenant, UUID turn, String client, UUID template) {
        return ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini")
                .tenantId(tenant).turnId(turn).clientId(client).build();
    }

    @Test
    void when_aTurnIsOpenedAndRecorded_then_exactlyOneRootWithFrozenNamingIsPublished() {
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();

        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", sink);
        ChatTurnSpanScope.close(trace(session, UUID.randomUUID(), UUID.randomUUID(), "client-1", template));

        assertThat(published).hasSize(1);
        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(1);
        ChatSpan root = spans.get(0);
        assertThat(root.name()).isEqualTo("chat.turn");
        assertThat(root.type()).isEqualTo("span");
        assertThat(root.rootObservation()).isTrue();
        assertThat(root.parentSpanId()).isNull();
        assertThat(root.attributes()).containsEntry("langfuse.observation.type", "span")
                .containsEntry("langfuse.trace.name", "chat.turn")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .doesNotContainKey("langfuse.observation.metadata.attempt");
    }

    @Test
    void when_twoAttemptsAreReported_then_twoChildrenShareTheRootTraceAndCarryAttemptAndFailure() {
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();

        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", sink);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(120, 340, 460);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptFailed(new IllegalStateException("SEGREDO", new java.net.http.HttpTimeoutException("timeout")));
        ChatTurnSpanScope.close(trace(session, null, null, null, template));

        assertThat(published).hasSize(1);
        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(3);
        ChatSpan root = spans.get(0);
        ChatSpan first = spans.get(1);
        ChatSpan second = spans.get(2);

        assertThat(List.of(first, second)).allSatisfy(child -> {
            assertThat(child.name()).isEqualTo("chat.provider.call");
            assertThat(child.type()).isEqualTo("generation");
            assertThat(child.rootObservation()).isFalse();
            assertThat(child.parentSpanId()).isEqualTo(root.spanId());
            assertThat(child.traceId()).isEqualTo(root.traceId());
            assertThat(child.attributes()).containsEntry("langfuse.trace.name", "chat.turn")
                    .containsEntry("provider", "OPENAI")
                    .containsEntry("model", "gpt-4o-mini");
        });
        assertThat(first.attributes())
                .containsEntry("langfuse.observation.metadata.attempt", "1")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .containsEntry("usage.input", "120")
                .containsEntry("usage.output", "340")
                .containsEntry("usage.total", "460")
                .doesNotContainKey("langfuse.observation.status_message");
        assertThat(second.attributes())
                .containsEntry("langfuse.observation.metadata.attempt", "2")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "HttpTimeoutException:timeout");
        assertThat(second.attributes().values()).noneMatch(v -> v.contains("SEGREDO") || v.contains("at br.com"));
        assertThat(root.attributes()).doesNotContainKey("langfuse.observation.metadata.attempt");
    }

    @Test
    void when_nullClientId_then_userIdIsOmittedFromRootAndChildren() {
        UUID session = UUID.randomUUID();

        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "m", sink);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(null, null, null);
        ChatTurnSpanScope.close(trace(session, null, null, null, UUID.randomUUID()));

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).allSatisfy(span -> assertThat(span.attributes()).doesNotContainKey("langfuse.user.id"));
    }

    @Test
    void when_twoSequentialTurns_then_theyGetDistinctTraceIdsAndDoNotLeakCorrelation() {
        UUID firstSession = UUID.randomUUID();
        UUID firstTenant = UUID.randomUUID();
        UUID secondTenant = UUID.randomUUID();

        ChatTurnSpanScope.open(firstSession, UUID.randomUUID(), AiProvider.OPENAI, "m", sink);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(null, null, null);
        ChatTurnSpanScope.close(trace(firstSession, firstTenant, UUID.randomUUID(), "one", UUID.randomUUID()));

        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", sink);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(null, null, null);
        ChatTurnSpanScope.close(trace(UUID.randomUUID(), secondTenant, UUID.randomUUID(), "two", UUID.randomUUID()));

        assertThat(published).hasSize(2);
        List<ChatSpan> first = published.get(0);
        List<ChatSpan> second = published.get(1);
        assertThat(first.get(0).traceId()).isNotEqualTo(second.get(0).traceId());
        assertThat(first).allSatisfy(span -> assertThat(span.attributes())
                .containsEntry("langfuse.trace.metadata.tenant_id", firstTenant.toString())
                .containsEntry("langfuse.user.id", "one"));
        assertThat(second).allSatisfy(span -> assertThat(span.attributes())
                .containsEntry("langfuse.trace.metadata.tenant_id", secondTenant.toString())
                .containsEntry("langfuse.user.id", "two"));
    }

    @Test
    void when_timestampedSpansAreCreated_then_startUsesRealNanosecondPrecision() {
        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", sink);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(1, 2, 3);
        ChatTurnSpanScope.close(trace(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "c",
                UUID.randomUUID()));

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).allSatisfy(span -> {
            assertThat(span.startTimeUnixNano()).isGreaterThan(1_700_000_000_000_000_000L);
            assertThat(span.endTimeUnixNano()).isGreaterThanOrEqualTo(span.startTimeUnixNano());
        });
        assertThat(spans).anySatisfy(span -> assertThat(span.startTimeUnixNano() % 1_000_000L).isNotZero());
    }

    @Test
    void when_sinkThrows_then_closeDoesNotPropagateAndTheRecorderDelegateStillRuns() {
        List<ChatTrace> delegated = new ArrayList<>();
        ChatSpanSink throwing = spans -> { throw new IllegalStateException("sink indisponível"); };
        ChatTrace trace = trace(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "client-1",
                UUID.randomUUID());
        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", throwing);

        assertThatCode(() -> new SpanningChatTraceRecorder(delegated::add).record(trace))
                .doesNotThrowAnyException();
        assertThat(delegated).containsExactly(trace);
    }

    @Test
    void when_sinkThrows_then_closeIfOpenDoesNotReplaceTheDomainFailure() {
        ChatSpanSink throwing = spans -> { throw new IllegalStateException("sink indisponível"); };
        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", throwing);

        assertThatCode(ChatTurnSpanScope::closeIfOpen).doesNotThrowAnyException();
    }

    @Test
    void when_aTurnIsAbandoned_then_closeIfOpenFlushesItWithoutAChatTrace() {
        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", sink);

        ChatTurnSpanScope.closeIfOpen();

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).hasSize(1);
        ChatTurnSpanScope.closeIfOpen();
        assertThat(published).hasSize(1);
    }
}
