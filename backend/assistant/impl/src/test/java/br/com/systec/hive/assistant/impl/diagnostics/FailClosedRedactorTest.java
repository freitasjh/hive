package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-08: exceção, expiração e política incompleta omitem ambas as chaves, sem perder metadados. */
class FailClosedRedactorTest {

    private static final Function<String, String> THROWS = text -> {
        throw new IllegalStateException("falha interna do redator");
    };
    private static final Function<String, String> EXPIRED = text -> {
        throw new IllegalStateException("redator expirado");
    };
    private static final Function<String, String> INCOMPLETE = text -> null;

    @Test
    void when_redactorThrows_then_bothTextKeysAreOmittedButMetadataSurvives() {
        assertFailClosed(THROWS);
    }

    @Test
    void when_redactorExpires_then_bothTextKeysAreOmittedButMetadataSurvives() {
        assertFailClosed(EXPIRED);
    }

    @Test
    void when_policyIsIncomplete_then_bothTextKeysAreOmittedButMetadataSurvives() {
        assertFailClosed(INCOMPLETE);
    }

    @Test
    void when_outputFailsAfterInputSucceeded_then_inputIsAlsoDiscarded() {
        List<List<ChatSpan>> published = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        TextRedactor redactor = new TextRedactor(text -> {
            if (calls.incrementAndGet() > 1) {
                throw new IllegalStateException("saida do redator falhou");
            }
            return RedactionPolicy.redact(text);
        });
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", published::add);

        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);
        ChatTurnSpanScope.attemptSucceeded(120, 340, 460);
        ChatTurnSpanScope.close(null);

        ChatSpan child = published.getFirst().stream().filter(s -> !s.rootObservation()).findFirst().orElseThrow();
        assertThat(child.attributes())
                .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output")
                .containsEntry("provider", "OPENAI")
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("usage.input", "120")
                .containsEntry("langfuse.observation.metadata.attempt", "1")
                .containsEntry("langfuse.observation.level", "DEFAULT");
    }

    private static void assertFailClosed(Function<String, String> failingPolicy) {
        List<List<ChatSpan>> published = new ArrayList<>();
        TextRedactor redactor = new TextRedactor(failingPolicy);
        UUID session = UUID.randomUUID();
        ChatTrace trace = ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini").build();
        ChatTurnSpanScope.open(session, trace.promptTemplateId(), AiProvider.OPENAI, "gpt-4o-mini", published::add);

        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);
        ChatTurnSpanScope.attemptSucceeded(120, 340, 460);
        ChatTurnSpanScope.close(trace);

        String serialized = published.toString();
        for (String literal : SensitiveFixture.LITERALS) {
            assertThat(serialized).doesNotContain(literal);
        }
        ChatSpan child = published.getFirst().stream().filter(s -> !s.rootObservation()).findFirst().orElseThrow();
        assertThat(child.attributes())
                .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output")
                .containsEntry("provider", "OPENAI")
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("usage.input", "120")
                .containsEntry("usage.output", "340")
                .containsEntry("usage.total", "460")
                .containsEntry("langfuse.observation.metadata.attempt", "1")
                .containsEntry("langfuse.observation.level", "DEFAULT");
    }
}
