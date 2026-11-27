package br.com.systec.hive.ai.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** NV-10 — langchain4j 1.19.0 and Boot 4.1.0 stay pinned; no reactor module, no Flux. */
class StreamingVersionGuardTest {

    @Test
    void when_readDependenciesPom_then_versionsRemainPinned() throws IOException {
        String pom = Files.readString(repoRoot().resolve("backend/dependencies/pom.xml"));

        assertThat(pom).contains("<langchain4j.version>1.19.0</langchain4j.version>")
                .contains("<spring.boot.version>4.1.0</spring.boot.version>")
                .doesNotContain("langchain4j-reactor");
    }

    @Test
    void when_scanMainSources_then_noFluxAndNoReactorDependency() throws IOException {
        Path backend = repoRoot().resolve("backend");
        try (Stream<Path> paths = Files.walk(backend)) {
            List<Path> hits = paths.filter(Files::isRegularFile).filter(p -> {
                String name = p.toString();
                if (name.endsWith("pom.xml")) {
                    try {
                        return Files.readString(p).contains("langchain4j-reactor");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                if (name.contains("/src/main/") && name.endsWith(".java")) {
                    try {
                        return Files.readString(p).contains("reactor.core.publisher.Flux");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                return false;
            }).toList();
            assertThat(hits).isEmpty();
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
