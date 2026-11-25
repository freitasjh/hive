package br.com.systec.hive.retention;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ACC-07/ACC-08 — modelo local da separacao de finalidades das chaves.
 *
 * ATENCAO: este e um fake que aplica a INFERENCIA "Full access" do ADR-004, nao uma prova contra o
 * Langfuse real. A prova de producao exige o IT com $LANGFUSE_REAL_BASE_URL, $APPLICATION_AUTH,
 * $VIEWER_AUTH e $FULL_AUTH (script retention-job/src/it/key-scope-it.sh); sem esse ambiente a
 * inferencia permanece pendente, conforme ADR §"Pendente (IT de 403)".
 */
class KeyScopeModelTest {

    @Test
    void whenIngestionKeyReads_thenItIsDenied() throws Exception {
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of())) {
            LangfuseClient client = client(langfuse, FakeLangfuse.APPLICATION_AUTH);
            HttpFailure failure = assertThrows(HttpFailure.class, () -> client.readExpiredRoots(java.time.Instant.EPOCH, java.time.Instant.parse("2026-10-26T00:00:00Z")));
            assertEquals(403, failure.status());
            assertTrue(langfuse.liveTraceIds.isEmpty());
        }
    }

    @Test
    void whenApplicationOrViewerDeletes_then403AndNoDeletion() throws Exception {
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of(List.of(FakeLangfuse.observation("keep", "2026-10-01T00:00:00Z", true))))) {
            for (String auth : List.of(FakeLangfuse.APPLICATION_AUTH, FakeLangfuse.VIEWER_AUTH)) {
                HttpFailure failure = assertThrows(HttpFailure.class,
                        () -> client(langfuse, auth).deleteTraces(List.of("keep")));
                assertEquals(403, failure.status());
            }
            assertTrue(langfuse.liveTraceIds.contains("keep"), "nenhuma chave debaixo privilegio exclui");
            assertEquals(2, langfuse.deleteBatches.size());
        }
    }

    @Test
    void whenViewerReadsAndFullKeyDeletes_thenBothSucceed() throws Exception {
        try (FakeLangfuse langfuse = new FakeLangfuse(List.of(List.of(FakeLangfuse.observation("gone", "2026-10-01T00:00:00Z", true))))) {
            List<?> read = client(langfuse, FakeLangfuse.VIEWER_AUTH).readExpiredRoots(java.time.Instant.EPOCH, java.time.Instant.parse("2026-10-26T00:00:00Z"));
            assertEquals(1, read.size());

            client(langfuse, FakeLangfuse.FULL_AUTH).deleteTraces(List.of("gone"));
            assertTrue(langfuse.liveTraceIds.isEmpty());
        }
    }

    private static LangfuseClient client(FakeLangfuse langfuse, String auth) {
        return new LangfuseClient(URI.create(langfuse.baseUri().toString()), new ApiCredentials(auth), HttpClient.newHttpClient());
    }
}
