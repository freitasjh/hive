package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.impl.util.ProviderMessages;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** ND-13 — streaming state is ephemeral: no entity, no collection, no migration. */
class StreamingDataModelGuardTest {

    @Test
    void when_inspectStreamingTypes_then_noJpaAnnotations() {
        for (Class<?> type : List.of(TurnStreamEvent.class, TurnStreamEvent.Reasoning.class,
                TurnStreamEvent.Text.class, TurnStreamEvent.Done.class, TurnStreamEvent.Failed.class,
                TurnStreamEventMapper.class)) {
            assertThat(type.getAnnotations()).as("%s", type.getSimpleName()).isEmpty();
        }
    }

    @Test
    void when_scanMainSources_then_noEntityOnStreamingTypes() throws IOException {
        Path ai = StreamingTestFixtures.repoRoot().resolve("backend/ai");
        try (Stream<Path> paths = Files.walk(ai)) {
            List<Path> hits = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("/src/main/") && p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String source = Files.readString(p);
                            return source.contains("@Entity") || source.contains("@Table")
                                    || source.contains("@Column");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    }).toList();
            assertThat(hits).isEmpty();
        }
    }

    @Test
    void when_scanMigrations_then_noStreamingTableCreated() throws IOException {
        try (Stream<Path> paths = Files.walk(StreamingTestFixtures.repoRoot().resolve("backend"))) {
            List<Path> hits = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("/dbupdate/"))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains("stream")
                            || p.getFileName().toString().toLowerCase().contains("thinking"))
                    .toList();
            assertThat(hits).isEmpty();
        }
    }

    @Test
    void when_measureThinking_then_onlyCharCountIsKept() throws Exception {
        assertThat(ProviderMessages.class.getDeclaredMethod("totalChars", List.class).getReturnType())
                .isEqualTo(int.class);
        assertThat(TurnStreamEvent.Reasoning.class.getRecordComponents()).hasSize(1);
        assertThat(TurnStreamEvent.Reasoning.class.getRecordComponents()[0].getName()).isEqualTo("text");
    }
}
