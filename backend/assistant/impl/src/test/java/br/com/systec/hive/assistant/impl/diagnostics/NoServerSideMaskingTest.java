package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-13: nenhum masking EE, Collector ou hook de SDK entra nesta fatia. */
class NoServerSideMaskingTest {

    @Test
    void when_backendIsScanned_then_noServerSideMaskingMechanismIsConfigured() throws Exception {
        Path backend = repoRoot().resolve("backend");
        List<String> needles = List.of(
                "LANGFUSE_INGESTION_MASKING_CALLBACK_" + "FAIL_CLOSED",
                "mask_" + "otel_spans",
                "Langfuse" + "SpanProcessor",
                "opentelemetry-" + "collector");
        try (Stream<Path> files = Files.walk(backend)) {
            List<String> hits = files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.toString();
                        return !name.contains("/target/")
                                && (name.endsWith(".java") || name.endsWith(".yml") || name.endsWith(".yaml")
                                || name.endsWith(".properties") || name.endsWith(".xml"));
                    })
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream().map(line -> path + ":" + line);
                        } catch (Exception e) {
                            throw new IllegalStateException("leitura de fonte falhou", e);
                        }
                    })
                    .filter(line -> needles.stream().anyMatch(line::contains))
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
