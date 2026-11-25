package br.com.systec.hive.retention;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** ACC-16 — gate de deploy: sem job ativo/cadencia verificavel, producao e bloqueada. */
class ProductionGateTest {
    private static final Path ROOT = Paths.get(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();
    private static final Path GATE = ROOT.resolve("docker/check-retention-gate.sh");

    @TempDir
    Path temp;

    @Test
    void whenCronIsMissing_thenProductionDeployIsDenied() throws Exception {
        Path cron = Files.writeString(temp.resolve("crontab"), "# sem job\n");
        assertEquals(1, gate(cron));
    }

    @Test
    void whenCronIsInstalledAndAuditIsConfigured_thenProductionDeployIsAllowed() throws Exception {
        Path cron = Files.writeString(temp.resolve("crontab"),
                "0 3 * * * root /usr/bin/docker compose -f /srv/hive/docker/docker-compose-retention.yml run --rm langfuse-retention\n");
        assertEquals(0, gate(cron));
    }

    private int gate(Path cron) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("bash", GATE.toString()).redirectErrorStream(true);
        builder.environment().put("RETENTION_CRON_FILE", cron.toString());
        builder.environment().put("REPO_ROOT", ROOT.toString());
        Process p = builder.start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), new String(p.getInputStream().readAllBytes()));
        return p.exitValue();
    }
}
