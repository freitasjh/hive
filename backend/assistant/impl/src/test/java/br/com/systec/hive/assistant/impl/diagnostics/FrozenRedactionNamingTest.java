package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-15: congela as duas chaves de texto e o formato dos placeholders para os ADRs downstream. */
class FrozenRedactionNamingTest {

    @Test
    void when_aTurnWithTextIsPublished_then_onlyTheTwoFrozenTextKeysAppearInTheChild() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();
        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);
        TextRedactor redactor = new TextRedactor();
        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);
        ChatTurnSpanScope.attemptSucceeded(1, 2, 3);
        ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini").build());

        List<ChatSpan> spans = published.getFirst();
        ChatSpan child = spans.stream().filter(s -> !s.rootObservation()).findFirst().orElseThrow();
        Set<String> textKeys = child.attributes().keySet().stream()
                .filter(key -> key.startsWith("langfuse.observation.") && key.contains("input")
                        || key.startsWith("langfuse.observation.") && key.contains("output"))
                .collect(Collectors.toSet());
        assertThat(textKeys).containsExactlyInAnyOrder("langfuse.observation.input", "langfuse.observation.output");
        assertThat(spans.stream().filter(ChatSpan::rootObservation).findFirst().orElseThrow().attributes().keySet())
                .doesNotContain("langfuse.observation.input", "langfuse.observation.output");
        assertThat(child.attributes().get("langfuse.observation.input")).isEqualTo(SensitiveFixture.REDACTED);
        assertThat(child.attributes().get("langfuse.observation.output")).isEqualTo(SensitiveFixture.REDACTED);
    }
}
