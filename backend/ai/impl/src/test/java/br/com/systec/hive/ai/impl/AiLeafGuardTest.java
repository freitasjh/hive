package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnReply;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** LEAF-08 — ADR-001 kept {@code ai/**} a leaf and kept thinking out of {@code TurnReply}. */
class AiLeafGuardTest {

    @Test
    void when_scanAiMainSources_then_noAssistantPublicApiOrHistoryTypes() throws IOException {
        assertThat(matchingLines(repoRoot().resolve("backend/ai"), "/src/main/",
                "import br.com.systec.hive.assistant", "import br.com.systec.hive.publicapi",
                "import br.com.systec.hive.history")).isEmpty();
    }

    @Test
    void when_inspectTurnReply_then_noNewThinkingTextField() {
        List<String> components = Arrays.stream(TurnReply.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components).hasSize(9);
        assertThat(components).noneMatch(name -> name.contains("thinking") || name.contains("reasoning"));
    }

    @Test
    void when_scanStreamingSources_then_gateReleaseOnlyInReleaseGateOnceAndNoReentrantLock() throws Exception {
        Path impl = repoRoot().resolve("backend/ai/impl/src/main/java");
        // AT-01/Emenda 2: the handler releases through one CAS-guarded callback; the registry owns
        // the counted release. No other source may call gate.release() directly.
        List<Path> gates = matchingLines(impl, "", "releaseGate.run()");

        assertThat(gates).hasSize(1);
        assertThat(Files.readString(gates.getFirst())).contains("gateReleased.compareAndSet(false, true)")
                .doesNotContain("void cancel() {\n        releaseGate.run()");
        assertThat(matchingLines(impl, "", "gate.release()")).isEmpty();
        assertThat(matchingLines(repoRoot().resolve("backend/ai"), "/src/main/",
                "new ReentrantLock", "import java.util.concurrent.locks.ReentrantLock")).isEmpty();
        assertThat(matchingLines(repoRoot().resolve("backend/assistant/impl"), "/src/main/",
                "new ReentrantLock", "import java.util.concurrent.locks.ReentrantLock")).isEmpty();
    }

    @Test
    void when_inspectMetrics_then_onlyContentFreeCountersAllowed() throws Exception {
        java.lang.reflect.Field field = StreamingMetrics.class.getDeclaredField("ALLOWED");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> allowed = (java.util.Set<String>) field.get(null);

        assertThat(allowed).containsExactlyInAnyOrder("stream.cancel.unsupported", "stream.failed",
                "stream.timeout", "stream.client_abort");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new StreamingMetrics().increment("thinking-SEGREDO"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<Path> sourceFiles(Path root) throws IOException {
        return Files.walk(root).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"));
    }

    private static List<Path> matchingLines(Path root, String pathFragment, String... needles) throws IOException {
        try (Stream<Path> paths = sourceFiles(root)) {
            return paths.filter(p -> p.toString().contains(pathFragment)).filter(p -> {
                try {
                    String source = Files.readString(p);
                    return Arrays.stream(needles).anyMatch(source::contains);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        }
    }

    private static Path repoRoot() {
        Path current = Path.of(".").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        assertThat(current).as("repo root with .git").isNotNull();
        return current;
    }
}
