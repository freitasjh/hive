package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FailureSpanMappingTest {

    @Test
    void when_aWrappedTimeoutFailsAnAttempt_then_statusMessageIsTheClassifierLabelOnly() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "m", published::add);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptFailed(new IllegalStateException(
                "prompt Sigiloso: PERGUNTA SIGILOSA", new java.net.http.HttpTimeoutException("read timed out")));
        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "m").build());

        ChatSpan failed = published.get(0).get(1);
        assertThat(failed.attributes())
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", FailureClassifier.causeOf(
                        new IllegalStateException("x", new java.net.http.HttpTimeoutException("read timed out"))));
        assertThat(failed.attributes().values())
                .noneMatch(v -> v.contains("Sigiloso") || v.contains("PERGUNTA") || v.contains("at br.com"));
    }

    @Test
    void when_anAttemptSucceeds_then_noErrorLevelIsWritten() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "m", published::add);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(null, null, null);
        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "m").build());

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).allSatisfy(span -> assertThat(span.attributes())
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .doesNotContainKey("langfuse.observation.status_message"));
    }
}
