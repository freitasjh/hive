package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Servidor OTLP fake: captura método, path, headers e corpo bruto de toda requisição. */
public final class OtlpCaptureServer implements AutoCloseable {

    public record Captured(String method, String path, Map<String, String> headers, byte[] body) {
        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    private final HttpServer server;
    private final List<Captured> captured = new CopyOnWriteArrayList<>();

    private OtlpCaptureServer(HttpServer server) {
        this.server = server;
    }

    public static OtlpCaptureServer start(Runnable onRequest) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        OtlpCaptureServer capture = new OtlpCaptureServer(server);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((key, values) ->
                    headers.put(key.toLowerCase(), String.join(",", values)));
            capture.captured.add(new Captured(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    headers, body));
            try {
                if (onRequest != null) {
                    onRequest.run();
                }
                byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return capture;
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + LangfuseOtlpExporterConfig.OTLP_TRACES_PATH;
    }

    public List<Captured> requests() {
        return List.copyOf(captured);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
