package br.com.systec.hive.retention;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

/** ACC-01..06/09 — proxy nginx real, gateway de capacidade e upstream falsos. */
class ProxyEnforcementTest {
    private static final Path ROOT = Paths.get(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();
    private static final Path TEMPLATE = ROOT.resolve("proxy/langfuse-proxy.conf.template");
    private static final String READ = "telemetry.read";
    private static final String ADMIN = "telemetry.admin";

    @TempDir
    static Path temp;

    private static HttpServer gateway;
    private static HttpServer upstream;
    private static int httpPort;
    private static int httpsPort;
    private static int adminHttpsPort;
    private static int authPort;
    private static int appPort;
    private static HttpClient client;
    private static Path conf;
    private static Path pid;

    @BeforeAll
    static void startProxy() throws Exception {
        assertTrue(Files.exists(TEMPLATE), "template do proxy ausente: " + TEMPLATE);
        httpPort = freePort(); httpsPort = freePort(); adminHttpsPort = freePort(); authPort = freePort(); appPort = freePort();

        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", authPort), 0);
        gateway.createContext("/verify", exchange -> {
            String wanted = exchange.getRequestURI().getQuery();
            String granted = exchange.getRequestHeaders().getFirst("X-Test-Capability");
            List<String> capabilities = granted == null ? List.of() : List.of(granted.split(","));
            String requested = wanted == null ? "" : wanted.substring("capability=".length());
            boolean allowed = capabilities.contains(requested)
                    || ("telemetry.read".equals(requested) && capabilities.contains(ADMIN));
            byte[] body = (allowed ? "{}" : "{\"message\":\"forbidden\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(allowed ? 200 : 403, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        gateway.start();

        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", appPort), 0);
        upstream.createContext("/api/public/v2/observations", exchange -> {
            byte[] body = "{\"data\":[{\"traceId\":\"test-trace\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.createContext("/api/admin/", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();

        Path cert = temp.resolve("cert.pem");
        Path key = temp.resolve("key.pem");
        run(120, "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-keyout", key.toString(), "-out", cert.toString(), "-subj", "/CN=localhost",
                "-addext", "subjectAltName=IP:127.0.0.1,DNS:localhost", "-days", "1");
        conf = temp.resolve("nginx.conf");
        pid = temp.resolve("nginx.pid");
        String text = Files.readString(TEMPLATE)
                .replace("listen 443 ssl;", "listen " + httpsPort + " ssl;")
                .replace("__HTTP_PORT__", String.valueOf(httpPort))
                .replace("__HTTPS_PORT__", String.valueOf(httpsPort))
                .replace("__ADMIN_HTTPS_PORT__", String.valueOf(adminHttpsPort))
                .replace("__AUTH_PORT__", String.valueOf(authPort))
                .replace("__UPSTREAM_PORT__", String.valueOf(appPort))
                .replace("__CERT__", cert.toString())
                .replace("__KEY__", key.toString())
                .replace("__ADMIN_CIDR__", "127.0.0.1")
                .replace("__PID__", pid.toString())
                .replace("__ERROR_LOG__", temp.resolve("error.log").toString())
                .replace("__ACCESS_LOG__", temp.resolve("access.log").toString());
        // Segundo listener: mesma politica, mas CIDR administrativo externo — a rede local deve ser negada.
        String external = "\n    server { listen " + adminHttpsPort + " ssl;"
                + " ssl_certificate " + cert + "; ssl_certificate_key " + key + ";"
                + " location ^~ /api/admin/ { allow 203.0.113.0/24; deny all; auth_request /_auth_admin; proxy_pass http://langfuse_upstream; }"
                + " location = /_auth_admin { internal; proxy_pass http://capability_gateway/verify?capability=telemetry.admin;"
                + " proxy_pass_request_body off; proxy_set_header Content-Length \"\"; } }\n";
        int close = text.lastIndexOf('}');
        text = text.substring(0, close) + external + text.substring(close);
        Files.writeString(conf, text);
        run(120, "nginx", "-c", conf.toString(), "-g", "error_log " + temp.resolve("boot.log") + " notice;");
        await(() -> Files.exists(pid), "nginx nao iniciou: " + Files.readString(temp.resolve("boot.log")));

        System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }}, new java.security.SecureRandom());
        client = HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(3)).build();
    }

    @AfterAll
    static void stopProxy() throws Exception {
        if (Files.exists(pid)) run(120, "nginx", "-s", "quit", "-c", conf.toString());
        if (gateway != null) gateway.stop(0);
        if (upstream != null) upstream.stop(0);
    }

    @Test
    void whenCapabilityIsMissingOrInvented_then403WithoutTraceBody() throws Exception {
        for (String cap : List.of("", "telemetry.reader")) {
            HttpResponse<String> response = get(httpsPort, "/api/public/v2/observations", cap);
            assertEquals(403, response.statusCode(), "cap=" + cap);
            assertFalse(response.body().contains("test-trace"), "corpo da negacao nao pode conter trace");
        }
    }

    @Test
    void whenReaderCapability_thenReadAllowedAndAdminDenied() throws Exception {
        assertEquals(200, get(httpsPort, "/api/public/v2/observations", READ).statusCode());
        assertTrue(get(httpsPort, "/api/public/v2/observations", READ).body().contains("test-trace"));
        assertEquals(403, get(httpsPort, "/api/admin/settings", READ).statusCode());
    }

    @Test
    void whenAdminCapability_thenReadAndAdminAreAllowed() throws Exception {
        assertEquals(200, get(httpsPort, "/api/public/v2/observations", ADMIN).statusCode());
        assertEquals(200, get(httpsPort, "/api/admin/settings", ADMIN).statusCode());
        assertEquals(200, get(httpsPort, "/api/public/v2/observations", READ + "," + ADMIN).statusCode());
    }

    @Test
    void whenSignupLoginOrPlainHttp_thenAccessIsRefused() throws Exception {
        for (String path : List.of("/signup", "/login", "/api/auth/signin", "/api/auth/signup")) {
            assertEquals(403, get(httpsPort, path, ADMIN).statusCode(), path);
        }
        assertThrows(IOException.class, () -> client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + "/api/public/v2/observations")).build(),
                HttpResponse.BodyHandlers.ofString()), "HTTP simples deve ser recusado sem resposta de dado");
    }

    @Test
    void whenAdminRequestComesFromExternalNetwork_then403() throws Exception {
        assertEquals(403, get(adminHttpsPort, "/api/admin/settings", ADMIN).statusCode());
    }

    private static HttpResponse<String> get(int port, String path, String capability) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + path));
        if (!capability.isEmpty()) request.header("X-Test-Capability", capability);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void await(java.util.function.BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) LockSupport.parkNanos(20_000_000L);
        assertTrue(condition.getAsBoolean(), message);
    }

    private static void run(long timeoutSeconds, String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS), "comando travou: " + command[0]);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
    }
}
