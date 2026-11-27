package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DAT-12 / NFR-04: zero migration, zero @Entity no caminho SSE; estado efêmero no emitter. */
class SseDataModelGuardIT {

    @Test
    void when_workingTreeIsComparedWithHead_then_noNewMigrationWasAdded() throws IOException {
        Path dbupdate = StreamingITFixtures.repoRoot().resolve("backend");
        List<Path> migrations;
        try (var stream = Files.walk(dbupdate)) {
            migrations = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("dbupdate"))
                    .filter(p -> p.getFileName().toString().matches("V\\d{8}\\d{3}__.*\\.sql"))
                    .toList();
        }
        // ADR-002 não cria tabela: o número de migrations do HEAD também é o da árvore de trabalho.
        assertThat(migrations).isEmpty();
    }

    @Test
    void when_serializersAndControllersAreScanned_then_noJpaAnnotationAppears() throws IOException {
        Path backend = StreamingITFixtures.repoRoot().resolve("backend");
        List<Path> sources;
        try (var stream = Files.walk(backend)) {
            sources = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> p.toString().contains("/serializer/") || p.toString().contains("/controller/"))
                    .toList();
        }
        for (Path source : sources) {
            String text = Files.readString(source);
            assertThat(text).as(source.toString())
                    .doesNotContain("@Entity", "@Table", "@Column", "@Embeddable");
        }
    }

    @Test
    void when_workingTreeIsComparedWithHead_then_noTurnReplyChangeWasMade() throws IOException {
        Process process = new ProcessBuilder("git", "diff", "--", "backend/ai/api/src/main/java/br/com/systec/hive/ai/api/domain/TurnReply.java")
                .directory(br.com.systec.hive.integrationtest.support.StreamingITFixtures.repoRoot().toFile())
                .redirectErrorStream(true).start();
        String diff = new String(process.getInputStream().readAllBytes());
        assertThat(diff).isEmpty();
    }
}
