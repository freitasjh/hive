package br.com.systec.hive.retention;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ACC-11, ACC-12, ACC-13, ACC-14, ACC-15, ACC-17, ACC-18. */
class RetentionJobTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-10-26T00:00:00Z"); // now truncado a dia - 30d

    @TempDir
    Path temp;

    @Test
    void whenExpiredRootsExist_thenJobSelectsOnlyRootsBeforeCutoffAndDeletesByTraceId() throws Exception {
        List<List<String>> pages = List.of(List.of(
                FakeLangfuse.observation("old-root", "2026-10-25T23:59:59Z", true),
                FakeLangfuse.observation("young-root", "2026-10-26T00:00:00Z", true),
                FakeLangfuse.observation("old-child", "2026-10-01T00:00:00Z", false)));
        try (FakeLangfuse langfuse = new FakeLangfuse(pages)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int exit = job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8));

            assertEquals(0, exit);
            String query = langfuse.readQueries.get(0);
            assertTrue(query.contains("isRootObservation=true"), query);
            assertTrue(query.contains("fields=core%2Cbasic") || query.contains("fields=core,basic"), query);
            assertTrue(query.contains("limit=1000"), query);
            assertTrue(query.contains("toStartTime=2026-10-26T00%3A00%3A00Z")
                    || query.contains("toStartTime=2026-10-26T00:00:00Z"), query);
            assertEquals(List.of(List.of("old-root")), langfuse.deleteBatches);

            JsonNode audit = audit(out);
            assertEquals(30, audit.get("retention_days").asInt());
            assertEquals(1, audit.get("candidates").asInt());
            assertEquals(1, audit.get("submitted").asInt());
            assertEquals(0, audit.get("errors").size());
            assertFalse(audit.toString().contains("young-root"));
            assertFalse(audit.has("deleted"), "campo congelado e submitted, nunca deleted");
        }
    }

    @Test
    void whenPageHasNoDataButCursor_thenJobFollowsCursorUntilCursorIsAbsent() throws Exception {
        List<List<String>> pages = List.of(List.of(), List.of(FakeLangfuse.observation("page-2-root", "2026-10-02T00:00:00Z", true)));
        try (FakeLangfuse langfuse = new FakeLangfuse(pages)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));

            assertEquals(2, langfuse.readQueries.size());
            assertTrue(langfuse.readQueries.get(1).contains("cursor=page-1"), langfuse.readQueries.get(1));
            assertEquals(List.of(List.of("page-2-root")), langfuse.deleteBatches);
        }
    }

    @Test
    void whenMoreThan1000TraceIds_thenDeleteBatchesRespectLimitOf1000() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 1001; i++) ids.add("trace-" + i);
        List<String> page = new ArrayList<>();
        for (String id : ids) page.add(FakeLangfuse.observation(id, "2026-10-01T00:00:00Z", true));
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of(page))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));

            assertEquals(2, langfuse.deleteBatches.size());
            assertEquals(1000, langfuse.deleteBatches.get(0).size());
            assertEquals(1, langfuse.deleteBatches.get(1).size());
            assertEquals(1001, audit(out).get("submitted").asInt());
            assertEquals(1001, audit(out).get("traceIds").size());
        }
    }

    @Test
    void whenDeleteBatchFails_thenAuditRecordsErrorAndOtherBatchesContinue() throws Exception {
        List<String> ids = List.of("a", "b");
        List<List<String>> pages = List.of(List.of(
                FakeLangfuse.observation("a", "2026-10-01T00:00:00Z", true),
                FakeLangfuse.observation("b", "2026-10-02T00:00:00Z", true)));
        try (FakeLangfuse langfuse = new FakeLangfuse(pages)) {
            langfuse.failBatch(0, 500);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(1, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));

            JsonNode audit = audit(out);
            assertEquals(2, audit.get("submitted").asInt());
            assertEquals(1, audit.get("errors").size());
            assertTrue(audit.get("errors").get(0).asText().contains("500"));
            assertTrue(langfuse.liveTraceIds.contains("a"), "submitted nao e confirmacao de exclusao");
        }
    }

    @Test
    void whenSecondRunUsesSameCutoff_thenNoCandidatesAndNoDelete() throws Exception {
        List<List<String>> pages = List.of(List.of(FakeLangfuse.observation("old", "2026-10-01T00:00:00Z", true)));
        try (FakeLangfuse langfuse = new FakeLangfuse(pages)) {
            ByteArrayOutputStream first = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, first).run(new PrintStream(first, true, StandardCharsets.UTF_8)));
            assertEquals(1, langfuse.deleteBatches.size());

            ByteArrayOutputStream second = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, second).run(new PrintStream(second, true, StandardCharsets.UTF_8)));
            assertEquals(1, langfuse.deleteBatches.size(), "reexecucao com mesmo cutoff nao duplica delete");
            assertEquals(0, audit(second).get("submitted").asInt());
        }
    }

    @Test
    void whenTracesAre29And31DaysOld_then29RemainsAnd31IsRemoved() throws Exception {
        List<List<String>> pages = List.of(List.of(
                FakeLangfuse.observation("t29", "2026-10-27T03:00:00Z", true),
                FakeLangfuse.observation("t31", "2026-10-25T03:00:00Z", true)));
        try (FakeLangfuse langfuse = new FakeLangfuse(pages)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));
            assertEquals(List.of(List.of("t31")), langfuse.deleteBatches);

            RetentionConfig config = new RetentionConfig(langfuse.baseUri(), new ApiCredentials(FakeLangfuse.VIEWER_AUTH),
                    30, NOW, temp.resolve("retention.lock"), "re-read");
            LangfuseClient reader = new LangfuseClient(config.baseUrl(), config.credentials(), java.net.http.HttpClient.newHttpClient());
            List<Observation> remaining = reader.readExpiredRoots(config.fromStartTime(), java.time.Instant.parse("2026-11-26T00:00:00Z"));
            assertEquals(1, remaining.size(), "prova real exige re-leitura v2: submitted nao e confirmacao");
            assertEquals("t29", remaining.get(0).traceId());
        }
    }

    @Test
    void whenLockIsHeld_thenJobDoesNotReadOrDelete() throws Exception {
        Path lock = Files.writeString(temp.resolve("retention.lock"), "held", StandardOpenOption.CREATE);
        try (var channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.WRITE);
             var ignored = channel.lock();
             FakeLangfuse langfuse = new FakeLangfuse(List.of(List.of()))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(2, job(langfuse, out, lock).run(new PrintStream(out, true, StandardCharsets.UTF_8)));
            assertTrue(langfuse.readQueries.isEmpty());
            assertTrue(langfuse.deleteBatches.isEmpty());
            assertFalse(audit(out).get("errors").get(0).asText().isBlank());
        }
    }

    @Test
    void whenCredentialsWouldReachAudit_thenOutputHasNoSecretAndNoForbiddenNaming() throws Exception {
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of(List.of()))) {
            // A credencial de teste e um valor obvio: se vazar, o teste falha.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(0, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));
            String log = out.toString(StandardCharsets.UTF_8);
            assertFalse(log.contains("full-test-token"));
            assertFalse(log.contains("Basic "));
            assertFalse(log.contains("\"deleted\""));
            assertTrue(log.lines().count() == 1, "uma linha JSON por evento");
        }
    }

    @Test
    void whenReadFails_thenAuditReportsErrorAndJobFails() throws Exception {
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of(List.of()))) {
            langfuse.denyListRequests();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            assertEquals(1, job(langfuse, out).run(new PrintStream(out, true, StandardCharsets.UTF_8)));
            assertTrue(langfuse.deleteBatches.isEmpty());
            assertEquals(1, audit(out).get("errors").size());
        }
    }

    private RetentionJob job(FakeLangfuse langfuse, ByteArrayOutputStream out, Path lock) {
        RetentionConfig config = new RetentionConfig(langfuse.baseUri(), new ApiCredentials(FakeLangfuse.FULL_AUTH),
                30, NOW, lock, "test-run");
        return new RetentionJob(config, new LangfuseClient(config.baseUrl(), config.credentials(), java.net.http.HttpClient.newHttpClient()));
    }

    private RetentionJob job(FakeLangfuse langfuse, ByteArrayOutputStream out) {
        return job(langfuse, out, temp.resolve("retention.lock"));
    }

    private JsonNode audit(ByteArrayOutputStream out) throws Exception {
        String text = out.toString(StandardCharsets.UTF_8).trim();
        JsonNode node = JSON.readTree(text);
        for (String field : List.of("run_id", "executed_at", "retention_days", "candidates", "submitted", "errors", "traceIds", "window", "timestamp")) {
            assertTrue(node.has(field), "campo de auditoria ausente: " + field);
        }
        assertEquals("test-run", node.get("run_id").asText());
        return node;
    }
}
