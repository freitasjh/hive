package br.com.systec.hive.retention;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adaptador HTTP da Public API. Usa java.net.http.HttpClient do JDK + Jackson. */
public final class LangfuseClient {
    static final int PAGE_LIMIT = 1000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI baseUrl;
    private final ApiCredentials credentials;
    private final HttpClient http;

    public LangfuseClient(URI baseUrl, ApiCredentials credentials, HttpClient http) {
        this.baseUrl = baseUrl;
        this.credentials = credentials;
        this.http = http;
    }

    /** Observations API v2: isRootObservation=true, fields=core,basic, janela [from,to), cursor ate ausente. */
    public List<Observation> readExpiredRoots(Instant fromStartTime, Instant toStartTime) throws IOException, HttpFailure, InterruptedException {
        List<Observation> rows = new ArrayList<>();
        String cursor = null;
        Set<String> seen = new HashSet<>();
        while (true) {
            String query = "isRootObservation=true&fromStartTime=" + encode(fromStartTime.toString())
                    + "&toStartTime=" + encode(toStartTime.toString())
                    + "&fields=" + encode("core,basic") + "&limit=" + PAGE_LIMIT;
            if (cursor != null) {
                if (!seen.add(cursor)) throw new HttpFailure(-1, "cursor repetido");
                query += "&cursor=" + encode(cursor);
            }
            URI uri = URI.create(baseUrl + "/api/public/v2/observations?" + query);
            HttpRequest request = HttpRequest.newBuilder(uri).header("Authorization", credentials.basicValue()).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new HttpFailure(response.statusCode(), "leitura de observations negada");
            JsonNode page = JSON.readTree(response.body());
            for (JsonNode row : page.path("data")) {
                String traceId = row.path("traceId").asText(null);
                String startTime = row.path("startTime").asText(null);
                if (traceId == null || startTime == null) continue;
                rows.add(new Observation(traceId, startTime, row.path("isRootObservation").asBoolean(false)));
            }
            JsonNode next = page.path("meta").path("cursor");
            if (next.isNull() || next.asText().isEmpty()) return rows;
            cursor = next.asText();
            // continua mesmo com data vazio: tamanho da pagina nao indica fim (contrato v2).
        }
    }

    /** DELETE /api/public/traces com 1..1000 IDs; resposta {"message":...} nao e confirmacao de exclusao. */
    public void deleteTraces(List<String> traceIds) throws IOException, HttpFailure, InterruptedException {
        if (traceIds.isEmpty() || traceIds.size() > PAGE_LIMIT) throw new IllegalArgumentException("lote deve ter 1..1000 IDs");
        URI uri = URI.create(baseUrl + "/api/public/traces");
        String body = JSON.writeValueAsString(java.util.Map.of("traceIds", traceIds));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Authorization", credentials.basicValue())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new HttpFailure(response.statusCode(), "delete de traces negado");
        // A resposta so traz message; submitted conta envios, nao confirmacao. Nada e logado aqui.
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
