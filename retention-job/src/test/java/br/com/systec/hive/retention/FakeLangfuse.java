package br.com.systec.hive.retention;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fake HTTP da API publica do Langfuse. Nao e Langfuse: modela apenas o contrato congelado no ADR-004
 * (Observations API v2 + DELETE /api/public/traces) e a separacao de finalidade das chaves.
 */
final class FakeLangfuse implements AutoCloseable {
    static final String APPLICATION_AUTH = basic("app-test-token");
    static final String VIEWER_AUTH = basic("viewer-test-token");
    static final String FULL_AUTH = basic("full-test-token");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    final List<String> readQueries = new ArrayList<>();
    final List<List<String>> deleteBatches = new ArrayList<>();
    final Set<String> liveTraceIds = new LinkedHashSet<>();
    private final List<List<String>> pages;
    private final Map<Integer, Integer> failingBatchStatus = new LinkedHashMap<>();
    private volatile boolean listRequestsAllowed = true;

    FakeLangfuse(List<List<String>> pages) throws IOException {
        this.pages = pages;
        for (List<String> page : pages) {
            for (String observation : page) {
                try {
                    liveTraceIds.add(JSON.readTree(observation).get("traceId").asText());
                } catch (Exception e) {
                    throw new IllegalArgumentException("observation de teste invalida", e);
                }
            }
        }
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/v2/observations", this::observations);
        server.createContext("/api/public/traces", this::traces);
        server.createContext("/api/public/otel/v1/traces", this::ingest);
        server.start();
    }

    URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    void failBatch(int batchIndex, int status) {
        failingBatchStatus.put(batchIndex, status);
    }

    void denyListRequests() {
        listRequestsAllowed = false;
    }

    private void observations(HttpExchange exchange) throws IOException {
        String scope = scope(exchange);
        readQueries.add(exchange.getRequestURI().getRawQuery());
        if (!"viewer".equals(scope) && !"full".equals(scope)) {
            respond(exchange, 403, "{\"message\":\"forbidden\"}");
            return;
        }
        if (!listRequestsAllowed) {
            respond(exchange, 500, "{\"message\":\"upstream unavailable\"}");
            return;
        }
        String cursor = query(exchange, "cursor");
        int pageIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            pageIndex = Integer.parseInt(cursor.substring(cursor.lastIndexOf('-') + 1));
        }
        ArrayNode data = JSON.createArrayNode();
        if (pageIndex < pages.size()) {
            for (String observation : pages.get(pageIndex)) {
                JsonNode row = JSON.readTree(observation);
                if (liveTraceIds.contains(row.get("traceId").asText())) data.add(row);
            }
        }
        String next = (pageIndex + 1 < pages.size()) ? "page-" + (pageIndex + 1) : null;
        String body = "{\"data\":" + data + ",\"meta\":{\"cursor\":" + (next == null ? "null" : "\"" + next + "\"") + "}}";
        respond(exchange, 200, body);
    }

    private void traces(HttpExchange exchange) throws IOException {
        if (!"DELETE".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"message\":\"method not allowed\"}");
            return;
        }
        String scope = scope(exchange);
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        List<String> traceIds = new ArrayList<>();
        JsonNode body = JSON.readTree(raw);
        body.get("traceIds").forEach(id -> traceIds.add(id.asText()));
        deleteBatches.add(traceIds);
        if (!"full".equals(scope)) {
            respond(exchange, 403, "{\"message\":\"forbidden\"}");
            return;
        }
        int status = failingBatchStatus.getOrDefault(deleteBatches.size() - 1, 200);
        if (status == 200) {
            liveTraceIds.removeAll(traceIds);
            respond(exchange, 200, "{\"message\":\"Traces deleted successfully\"}");
        } else {
            respond(exchange, status, "{\"message\":\"delete failed\"}");
        }
    }

    private void ingest(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String scope = scope(exchange);
        respond(exchange, "application".equals(scope) || "full".equals(scope) ? 200 : 403, "{}");
    }

    private String scope(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null) return "none";
        if (APPLICATION_AUTH.equals(header)) return "application";
        if (VIEWER_AUTH.equals(header)) return "viewer";
        if (FULL_AUTH.equals(header)) return "full";
        return "unknown";
    }

    private static String query(HttpExchange exchange, String name) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return null;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    static String basic(String value) {
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    static String observation(String traceId, String startTime, boolean root) {
        return "{\"id\":\"obs-" + traceId + "\",\"traceId\":\"" + traceId + "\",\"startTime\":\"" + startTime
                + "\",\"isRootObservation\":" + root + "}";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
