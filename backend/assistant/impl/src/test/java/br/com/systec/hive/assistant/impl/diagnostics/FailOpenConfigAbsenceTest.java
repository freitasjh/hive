package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-16: nenhuma variável de config transforma falha em envio do texto original. */
class FailOpenConfigAbsenceTest {

    @Test
    void when_plausibleFlagsAreSet_then_forcedFailureStillOmitsBothTextKeys() {
        List<String> flags = List.of(
                "HIVE_REDACTION_BEST_EFFORT", "LANGFUSE_REDACTION_DISABLED",
                "HIVE_TELEMETRY_REDACTION_MODE", "LANGFUSE_INGESTION_MASKING_CALLBACK_FAIL_" + "OPEN");
        for (String flag : flags) {
            String previous = System.getProperty(flag);
            System.setProperty(flag, "true");
            try {
                List<List<ChatSpan>> published = new ArrayList<>();
                TextRedactor redactor = new TextRedactor(text -> {
                    throw new IllegalStateException("forced");
                });
                UUID session = UUID.randomUUID();
                ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", published::add);
                ChatTurnSpanScope.attemptStarted();
                redactor.captureInput(SensitiveFixture.RAW);
                redactor.captureOutput(SensitiveFixture.RAW);
                ChatTurnSpanScope.attemptSucceeded(1, 2, 3);
                ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini")
                        .build());

                assertThat(published.toString()).doesNotContain(SensitiveFixture.LITERALS.toArray(String[]::new));
                ChatSpan child = published.getFirst().stream().filter(s -> !s.rootObservation()).findFirst().orElseThrow();
                assertThat(child.attributes()).doesNotContainKeys(
                        SpanAttributeMapper.KEY_INPUT, SpanAttributeMapper.KEY_OUTPUT);
            } finally {
                if (previous == null) {
                    System.clearProperty(flag);
                } else {
                    System.setProperty(flag, previous);
                }
            }
        }
    }

    @Test
    void when_assistantSourcesAreScanned_then_noFailOpenFlagExists() throws Exception {
        Path source = repoRoot().resolve("backend/assistant");
        String needle = "fail" + ".open";
        String upper = "FAIL_" + "OPEN";
        try (Stream<Path> files = Files.walk(source)) {
            List<Path> hits = files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String text = Files.readString(path);
                            return text.contains(needle) || text.contains(upper);
                        } catch (Exception e) {
                            throw new IllegalStateException("leitura de fonte falhou", e);
                        }
                    })
                    .toList();
            assertThat(hits).isEmpty();
        }
    }

    private static Path repoRoot() {
        Path path = Path.of("").toAbsolutePath().normalize();
        while (path != null && !Files.isDirectory(path.resolve(".spec"))) {
            path = path.getParent();
        }
        assertThat(path).as("repo root com .spec/").isNotNull();
        return path;
    }
}
