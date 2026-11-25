package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-01/RED-17: decisão registrada com fonte datada e rastreabilidade 1:1, sem dependência nova. */
class RedactorDecisionGateTest {

    @Test
    void when_redactorDecisionIsRead_then_emendaHasDatedSourcesAndNoDependencyWasAdded() throws Exception {
        Path adr = adrDir().resolve("ADR.md");
        Path spec = adrDir().resolve("SPEC.md");
        String adrText = Files.readString(adr, StandardCharsets.UTF_8);
        String specText = Files.readString(spec, StandardCharsets.UTF_8);

        assertThat(adrText).contains("Emenda RED-01", "2026-11-25",
                "https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html",
                "zero dependência nova", "alfanumérico");
        assertThat(specText).contains("2026-11-25", "CNPJ alfanumérico", "Pattern.compile");

        Process process = new ProcessBuilder("git", "diff", "--name-only", "--", "backend/pom.xml")
                .directory(repoRoot().toFile())
                .redirectErrorStream(true)
                .start();
        String diff = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertThat(process.waitFor()).isZero();
        assertThat(diff).as("zero diff em backend/pom.xml").isEmpty();
    }

    @Test
    void when_tasksAreAudited_then_18RedTasksMapToOneToOneToRequirements() throws Exception {
        String tasks = Files.readString(adrDir().resolve("TASKS.md"), StandardCharsets.UTF_8);
        assertThat(tasks.lines().findFirst().orElseThrow()).isEqualTo("<!-- scope: adr/ADR-003 -->");

        long sections = tasks.lines().filter(line -> line.startsWith("### RED-")).count();
        assertThat(sections).isEqualTo(18);
        for (int i = 1; i <= 18; i++) {
            assertThat(tasks).contains("### RED-" + String.format("%02d", i));
        }
    }

    private static Path adrDir() {
        Path dir = repoRoot().resolve(".spec/prd/telemetria-ia-langfuse/ADRs/ADR-003-redacao-origem");
        assertThat(Files.isDirectory(dir)).isTrue();
        return dir;
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
