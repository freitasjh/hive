package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptCounterTest {

    @Test
    void when_threeAttemptsHappen_then_childrenAreNumberedFromOneAndRootHasNoAttempt() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "m", published::add);
        for (int i = 0; i < 3; i++) {
            ChatTurnSpanScope.attemptStarted();
            ChatTurnSpanScope.attemptSucceeded(null, null, null);
        }
        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "m").build());

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(4);
        assertThat(spans.get(0).attributes()).doesNotContainKey("langfuse.observation.metadata.attempt");
        assertThat(spans.subList(1, 4)).extracting(s -> s.attributes().get("langfuse.observation.metadata.attempt"))
                .containsExactly("1", "2", "3");
    }

    @Test
    void when_anErrorArrivesWithoutARequest_then_noAdditionalChildIsInvented() {
        List<List<ChatSpan>> published = new ArrayList<>();
        ChatTurnSpanScope.open(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m", published::add);
        ChatTurnSpanScope.attemptFailed(new RuntimeException("x"));
        ChatTurnSpanScope.close(null);

        assertThat(published.get(0)).hasSize(1);
    }
}
