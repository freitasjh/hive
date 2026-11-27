package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.impl.SessionGateRegistry;
import br.com.systec.hive.ai.impl.TurnStreamEventMapper;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** NV-10 / ND-13: pinned stack, ephemeral streaming state, one shared semaphore and no orphan lock. */
class StreamingVersionAndDataModelIT {

    @Test
    void when_stackPinsAreRead_then_langchain4jRemainsPinnedWithoutReactor() throws IOException {
        String pom = Files.readString(StreamingITFixtures.repoRoot().resolve("backend/dependencies/pom.xml"));
        assertThat(pom).contains("<langchain4j.version>1.19.0</langchain4j.version>")
                .contains("<spring.boot.version>4.1.0</spring.boot.version>")
                .doesNotContain("langchain4j-reactor");

        List<Path> hits;
        try (Stream<Path> paths = Files.walk(StreamingITFixtures.repoRoot().resolve("backend"))) {
            hits = paths.filter(Files::isRegularFile).filter(p -> {
                String name = p.toString();
                if (name.endsWith("pom.xml")) {
                    try { return Files.readString(p).contains("langchain4j-reactor"); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                }
                if (name.contains("/src/main/") && name.endsWith(".java")) {
                    try { return Files.readString(p).contains("reactor.core.publisher.Flux"); }
                    catch (IOException e) { throw new UncheckedIOException(e); }
                }
                return false;
            }).toList();
        }
        assertThat(hits).isEmpty();
    }

    @Test
    void when_streamingTypesAreInspected_then_noJpaStateAndNoStreamingMigration() throws IOException {
        for (Class<?> type : List.of(TurnStreamEvent.class, TurnStreamEvent.Reasoning.class,
                TurnStreamEvent.Text.class, TurnStreamEvent.Done.class, TurnStreamEvent.Failed.class,
                TurnStreamEventMapper.class)) {
            assertThat(type.getAnnotations()).as("%s", type.getSimpleName()).isEmpty();
        }
        try (Stream<Path> paths = Files.walk(StreamingITFixtures.repoRoot().resolve("backend"))) {
            List<Path> hits = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("/dbupdate/")
                            && (p.getFileName().toString().toLowerCase().contains("stream")
                                || p.getFileName().toString().toLowerCase().contains("thinking")))
                    .toList();
            assertThat(hits).isEmpty();
        }
    }

    @Test
    void when_gateSourcesAreInspected_then_oneFairSemaphoreAndNoOrphanLock() throws Exception {
        Path ai = StreamingITFixtures.repoRoot().resolve("backend/ai/impl/src/main/java");
        long semaphores;
        try (Stream<Path> paths = Files.walk(ai)) {
            semaphores = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("new Semaphore(1, true)"); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    }).count();
        }
        assertThat(semaphores).isEqualTo(1);
        assertThat(SessionGateRegistry.class.getMethod("gateFor", java.util.UUID.class)).isNotNull();
        assertThat(SessionGateRegistry.class.getMethod("shared")).isNotNull();

        Path assistant = StreamingITFixtures.repoRoot().resolve("backend/assistant/impl/src/main/java");
        try (Stream<Path> paths = Files.walk(assistant)) {
            List<Path> orphanLocks = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try { return Files.readString(p).contains("lockFor"); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    }).toList();
            assertThat(orphanLocks).isEmpty();
        }
    }
}
