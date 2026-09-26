package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.commons.impl.http.PinnedHttpClient;
import br.com.systec.hive.knowledge.impl.security.KbUrlGuard;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbPinnedConnectionTest {

    private KbIngestionService service() {
        return new KbIngestionService(null, null, null, null, null, null);
    }

    @Test
    void when_fetchHttp_with_localServer_then_routesByHostHeaderToPinnedIp() throws Exception {
        AtomicReference<String> seenHost = new AtomicReference<>();
        byte[] payload = "hello-kb".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/hello", exchange -> {
            seenHost.set(exchange.getRequestHeaders().getFirst("Host"));
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            URI uri = new URI("http://localhost:" + port + "/hello");
            InetAddress pinned = InetAddress.getByName("127.0.0.1");

            KbUrlGuard.RedirectAwareContent content = service().fetchHttp(uri, pinned);

            assertThat(new String(content.bytes(), StandardCharsets.UTF_8)).isEqualTo("hello-kb");
            assertThat(content.contentType()).contains("text/plain");
            assertThat(seenHost.get()).isEqualTo("localhost:" + port);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void when_fetchHttp_with_redirect_then_returnsLocation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/old", exchange -> {
            exchange.getResponseHeaders().set("Location", "/new");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            KbUrlGuard.RedirectAwareContent content = service()
                    .fetchHttp(new URI("http://localhost:" + port + "/old"), InetAddress.getByName("127.0.0.1"));

            assertThat(content.location()).isEqualTo("/new");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void when_assertRemotePinned_with_divergentAddress_then_throws() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.start();
        try {
            int port = server.getAddress().getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 2000);

                assertThatThrownBy(() -> PinnedHttpClient.assertRemotePinned(
                        socket, InetAddress.getByName("127.0.0.2"), port))
                        .isInstanceOf(java.io.IOException.class);
                PinnedHttpClient.assertRemotePinned(socket, InetAddress.getByName("127.0.0.1"), port);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void when_readResponse_with_chunkedBody_then_decodes() throws Exception {
        String raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n";
        PinnedHttpClient.PinnedResponse response = PinnedHttpClient.readResponse(
                new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)), 1024);

        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("hello");
    }
}
