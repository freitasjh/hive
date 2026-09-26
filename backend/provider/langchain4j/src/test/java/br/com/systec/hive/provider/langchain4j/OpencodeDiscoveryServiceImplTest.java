package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.DiscoveredModels;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.api.exception.DiscoveryFailedException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpencodeDiscoveryServiceImplTest {

    /**
     * Paths esperados, hardcoded de proposito: derivar do VendorCatalog tornaria o teste
     * tautologico. A regress que este teste cobre e o "/v1" do baseUrl vendor appearing
     * duas vezes ("/v1/v1/models") quando o path passa a ser montado por sufixo.
     */
    private static final String GO_MODELS_PATH = "/zen/go/v1/models";
    private static final String ZEN_MODELS_PATH = "/zen/v1/models";
    private static final String UNEXPECTED_PATH_BODY = "{\"error\":\"unexpected discovery path\"}";

    private final OpencodeDiscoveryServiceImpl service = new OpencodeDiscoveryServiceImpl();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServer(int status, String body) throws IOException {
        AtomicReference<String> payload = new AtomicReference<>(body);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            lastPath.set(path);
            if (!GO_MODELS_PATH.equals(path) && !ZEN_MODELS_PATH.equals(path)) {
                byte[] rejected = UNEXPECTED_PATH_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, rejected.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(rejected);
                }
                return;
            }
            byte[] bytes = payload.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Reproduz o baseUrl vendor de producao (mesmo path da constante) apontado para o servidor local. */
    private String vendorBaseUrl(String vendorBaseUrl) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + URI.create(vendorBaseUrl).getPath();
    }

    private String goBaseUrl() {
        return vendorBaseUrl(VendorCatalog.OPENCODE_GO_BASE_URL);
    }

    private String zenBaseUrl() {
        return vendorBaseUrl(VendorCatalog.OPENCODE_ZEN_BASE_URL);
    }

    private static String modelsJson(String... ids) {
        StringBuilder sb = new StringBuilder("{\"object\":\"list\",\"data\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(ids[i]).append("\",\"object\":\"model\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void when_discover_with_mixedList_then_filtersDenylistedAndInvalid() throws Exception {
        startServer(200, modelsJson(
                "mimo-v2.5", "grok-4.5", "gpt-5.6-luna", "minimax-m2.5", "qwen3.8-max",
                "muse-spark-1.3-contributor", "bad id!", "kimi-k2.5"));

        DiscoveredModels result = service.discover(AiProvider.OPENCODE_GO, goBaseUrl());

        assertThat(result.models()).containsExactly("mimo-v2.5", "kimi-k2.5");
        assertThat(result.filteredOut()).extracting("reason")
                .contains("DENYLISTED", "INVALID_ID");
        assertThat(result.filteredOut()).extracting("id")
                .contains("grok-4.5", "gpt-5.6-luna", "minimax-m2.5", "qwen3.8-max",
                        "muse-spark-1.3-contributor", "bad id!");
        assertThat(result.cachedAt()).isNotNull();
    }

    @Test
    void when_discover_with_goVendorBaseUrl_then_requestsSingleV1ModelsPath() throws Exception {
        startServer(200, modelsJson("mimo-v2.5"));

        DiscoveredModels result = service.discover(AiProvider.OPENCODE_GO, goBaseUrl());

        assertThat(result.models()).containsExactly("mimo-v2.5");
        assertThat(lastPath.get()).isEqualTo(GO_MODELS_PATH);
        assertThat(lastPath.get()).doesNotContain("/v1/v1");
    }

    @Test
    void when_discover_with_zenVendorBaseUrl_then_requestsSingleV1ModelsPath() throws Exception {
        startServer(200, modelsJson("claude-sonnet-4-5"));

        DiscoveredModels result = service.discover(AiProvider.OPENCODE_ZEN, zenBaseUrl());

        assertThat(result.models()).containsExactly("claude-sonnet-4-5");
        assertThat(lastPath.get()).isEqualTo(ZEN_MODELS_PATH);
        assertThat(lastPath.get()).doesNotContain("/v1/v1");
    }

    @Test
    void when_discover_twice_then_secondComesFromCache() throws Exception {
        startServer(200, modelsJson("mimo-v2.5"));

        DiscoveredModels first = service.discover(AiProvider.OPENCODE_GO, goBaseUrl());
        server.stop(0);

        DiscoveredModels second = service.discover(AiProvider.OPENCODE_GO, goBaseUrl());

        assertThat(second.cachedAt()).isEqualTo(first.cachedAt());
        assertThat(second.models()).containsExactly("mimo-v2.5");
    }

    @Test
    void when_evict_then_nextDiscoverRefetches() throws Exception {
        startServer(200, modelsJson("mimo-v2.5"));

        DiscoveredModels first = service.discover(AiProvider.OPENCODE_ZEN, zenBaseUrl());
        service.evict(AiProvider.OPENCODE_ZEN);
        DiscoveredModels second = service.discover(AiProvider.OPENCODE_ZEN, zenBaseUrl());

        assertThat(second.cachedAt()).isAfterOrEqualTo(first.cachedAt());
        assertThat(second.models()).containsExactly("mimo-v2.5");
    }

    @Test
    void when_remoteFails_then_throwsDiscoveryFailed() throws Exception {
        startServer(500, "boom");

        assertThatThrownBy(() -> service.discover(AiProvider.OPENCODE_GO, goBaseUrl()))
                .isInstanceOf(DiscoveryFailedException.class);
    }

    @Test
    void when_remoteBodyMalformed_then_throwsDiscoveryFailed() throws Exception {
        startServer(200, "not-json{{{");

        assertThatThrownBy(() -> service.discover(AiProvider.OPENCODE_ZEN, zenBaseUrl()))
                .isInstanceOf(DiscoveryFailedException.class);
    }

    @Test
    void when_discover_with_unsupportedProvider_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.discover(AiProvider.OPENAI))
                .isInstanceOf(IllegalArgumentException.class);
        service.evict(AiProvider.OPENAI);
    }
}
