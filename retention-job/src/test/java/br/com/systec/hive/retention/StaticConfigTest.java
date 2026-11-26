package br.com.systec.hive.retention;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** ACC-01..10, 13, 16, 18..23 — varreduras estaticas e documentais (somente leitura de .spec/**). */
class StaticConfigTest {
    private static final Path ROOT = Paths.get(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();
    private static final Path ADR = ROOT.resolve(".spec/archive/20261126-prd-telemetria-ia-langfuse/ADRs/ADR-004-acesso-retencao");
    private static final Path PROXY = ROOT.resolve("proxy/langfuse-proxy.conf.template");
    private static final Path PROXY_DOC = ROOT.resolve("proxy/README.md");
    private static final Path COMPOSE = ROOT.resolve("docker/docker-compose-retention.yml");
    private static final Path CRON = ROOT.resolve("docker/cron/langfuse-retention.cron");
    private static final Path GATE = ROOT.resolve("docker/check-retention-gate.sh");
    private static final Path IT_SCRIPT = ROOT.resolve("retention-job/src/it/key-scope-it.sh");

    @Test
    void whenProxyConfigInspected_thenCapabilitiesAreExactAndDefaultDenyExists() throws Exception {
        String conf = read(PROXY);
        assertTrue(conf.contains("telemetry.read"), ACC("02"));
        assertTrue(conf.contains("telemetry.admin"), ACC("02"));
        assertFalse(conf.contains("telemetry_read"), ACC("02"));
        assertEquals(2, count(conf, "telemetry\\.read"), ACC("02"));
        assertEquals(2, count(conf, "telemetry\\.admin"), ACC("02"));
        assertTrue(conf.contains("auth_request"), ACC("01"));
        assertTrue(conf.contains("deny all;"), ACC("01") + ACC("05"));
        assertTrue(conf.contains("proxy_pass http://langfuse_upstream"), ACC("03"));
        assertTrue(conf.contains("proxy_pass http://capability_gateway"), ACC("03") + ACC("04"));
    }

    @Test
    void whenHardeningInspected_thenPasswordLoginSignupAndExternalAdminAreBlocked() throws Exception {
        String conf = read(PROXY);
        assertTrue(conf.contains("signup") && conf.contains("signin") && conf.contains("login"), ACC("05"));
        assertTrue(conf.contains("allow __ADMIN_CIDR__;"), ACC("05"));
        String doc = read(PROXY_DOC);
        assertTrue(doc.contains("AUTH_DISABLE_SIGNUP=true"), ACC("05"));
        assertTrue(doc.contains("AUTH_DISABLE_USERNAME_PASSWORD=true"), ACC("05"));
        assertTrue(doc.contains("ADMIN_API_KEY"), ACC("05"));
    }

    @Test
    void whenTlsInspected_thenPlainHttpIsRefused() throws Exception {
        String conf = read(PROXY);
        assertTrue(conf.contains("listen 443 ssl;"), ACC("06"));
        assertTrue(conf.contains("return 444;"), ACC("06"));
    }

    @Test
    void whenJobInvocationInspected_thenItRunsOutsideBackendAndProxy() throws Exception {
        String compose = read(COMPOSE);
        assertTrue(compose.contains("hive-retention-job"), ACC("23"));
        assertTrue(compose.contains("LANGFUSE_RETENTION_AUTH"), ACC("23"));
        assertFalse(compose.contains("telemetry.read") || compose.contains("telemetry.admin"), ACC("01"));
        assertFalse(read(ROOT.resolve("proxy/README.md")).contains("cron do host") == false, ACC("01"));
        String cron = read(CRON);
        assertTrue(cron.contains("0 3 * * *"), ACC("20"));
        assertTrue(cron.contains("docker compose"), ACC("20"));
        assertFalse(cron.contains("sk-"), ACC("09"));
    }

    @Test
    void whenDeleteKeyScopeInspected_thenNoKeyIsInBackendAndRealItIsAvailable() throws Exception {
        String it = read(IT_SCRIPT);
        assertTrue(it.contains("$APPLICATION_AUTH") && it.contains("$VIEWER_AUTH"), ACC("07"));
        assertTrue(it.contains("DELETE") && it.contains("403"), ACC("07"));
        assertFalse(it.matches("(?s).*sk-[A-Za-z0-9]{8,}.*"), ACC("09"));
        List<Path> backendEnv = Files.walk(ROOT.resolve("backend"))
                .filter(p -> p.getFileName().toString().endsWith(".java") || p.getFileName().toString().endsWith(".properties"))
                .toList();
        for (Path p : backendEnv) {
            String source = read(p);
            assertFalse(source.contains("LANGFUSE_RETENTION_AUTH"), ACC("07") + " em " + p);
        }
    }

    @Test
    void whenForbiddenEquivalenceScanned_thenProxyIsNeverCalledProjectRbac() throws Exception {
        for (Path p : List.of(PROXY, PROXY_DOC, COMPOSE, CRON)) {
            String text = read(p).toLowerCase();
            assertFalse(text.contains("rbac por projeto"), ACC("10") + " em " + p);
            assertFalse(text.contains("enterprise"), ACC("19") + " em " + p);
            assertFalse(text.contains("masking"), ACC("19") + " em " + p);
            assertFalse(text.contains("project.role"), ACC("19") + " em " + p);
        }
    }

    @Test
    void whenRetentionParamsInspected_thenWindowIsExplicitAndAuditable() throws Exception {
        assertTrue(read(COMPOSE).contains("RETENTION_DAYS: \"30\""), ACC("13"));
        assertTrue(read(PROXY_DOC).contains("03:00"), ACC("20"));
        assertTrue(read(PROXY_DOC).contains("time de IA"), ACC("20"));
        assertTrue(read(PROXY_DOC).contains("agregador"), ACC("20"));
        assertTrue(read(PROXY_DOC).contains("submitted"), ACC("20"));
    }

    @Test
    void whenProductionGateInspected_thenInactiveJobBlocksDeploy() throws Exception {
        String script = read(GATE);
        assertTrue(script.contains("RETENTION_CRON_FILE"), ACC("16"));
        assertTrue(script.contains("docker compose"), ACC("16"));
        assertTrue(script.contains("exit 1"), ACC("16"));
    }

    @Test
    void whenMainSourcesScanned_thenNoDeletedFieldAndNoSecretLiteral() throws Exception {
        List<Path> sources = Files.walk(ROOT.resolve("retention-job/src/main"))
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .toList();
        assertFalse(sources.isEmpty(), ACC("23"));
        for (Path p : sources) {
            String source = read(p);
            assertFalse(source.contains("\"deleted\""), ACC("18") + " em " + p);
            assertFalse(source.contains("sk-"), ACC("09") + " em " + p);
            assertFalse(source.contains("telemetry.read"), ACC("13") + " em " + p);
        }
    }

    @Test
    void whenAdrDocumentsAudited_thenOperationalDecisionsAndTraceabilityArePresent() throws Exception {
        String adr = read(ADR.resolve("ADR.md"));
        String spec = read(ADR.resolve("SPEC.md"));
        String tasks = read(ADR.resolve("TASKS.md"));
        assertTrue(adr.contains("<!-- scope: adr/ADR-004 -->") == false, "scope e das SPEC/TASKS, nao do ADR");
        assertTrue(spec.startsWith("<!-- scope: adr/ADR-004 -->"), ACC("21"));
        assertTrue(tasks.startsWith("<!-- scope: adr/ADR-004 -->"), ACC("21"));
        assertTrue(adr.contains("Emenda ACC-23"), ACC("23"));
        assertTrue(adr.contains("2026-11-25"), ACC("23"));
        assertTrue(adr.contains("https://langfuse.com/docs/api-and-data-platform/features/observations-api"), ACC("23"));
        assertTrue(adr.contains("INFERNÊNCIA"), ACC("23"));
        assertTrue(adr.contains("time de IA") && adr.contains("03:00"), ACC("20"));
        assertTrue(adr.contains("`submitted`"), ACC("20"));
        for (String line : adr.lines().filter(l -> l.contains("https://")).toList()) {
            assertTrue(line.contains("2026-11-25"), ACC("21") + " URL sem data: " + line);
        }
        assertEquals(23, count(tasks, "^### ACC-"), ACC("21"));
        for (int i = 1; i <= 17; i++) assertEquals(1, count(spec, "RF-" + String.format("%02d", i) + " —"), ACC("21"));
        for (int i = 1; i <= 6; i++) assertEquals(1, count(spec, "NFR-" + String.format("%02d", i) + " —"), ACC("21"));
    }

    private static String read(Path p) throws IOException {
        assertTrue(Files.exists(p), "arquivo ausente: " + p);
        return Files.readString(p);
    }

    private static long count(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
        long n = 0;
        while (m.find()) n++;
        return n;
    }

    private static String ACC(String id) {
        return "ACC-" + id;
    }
}
