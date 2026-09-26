package br.com.systec.hive.commons.impl.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression suite for the TLS hostname-verification stub defect.
 *
 * <p>{@code HttpsURLConnection.getDefaultHostnameVerifier()} is an always-false stub
 * (JDK 25, {@code java.base/javax/net/ssl/HttpsURLConnection.java:198-204}), so
 * {@link PinnedHttpClient} rejected <em>every</em> pinned HTTPS fetch. The real check now
 * happens inside the handshake via
 * {@code SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")}.
 *
 * <p>Keystores are generated at test time by {@code keytool} into a JUnit {@link TempDir};
 * no certificate or keystore is ever committed to the repository and no external network
 * access is required. Both test certificates are self-signed and additionally installed as
 * trust anchors in the JVM default {@link SSLContext} for the duration of this class.
 */
class PinnedHttpClientTlsTest {

    private static final String PASSWORD = "changeit";
    private static final byte[] PAYLOAD = "pinned-tls-ok".getBytes(StandardCharsets.UTF_8);
    private static final long MAX_BYTES = 4096L;
    private static final int TIMEOUT_MS = 3000;

    @TempDir
    static Path keystoreDir;

    private static Path matchingKeystore;
    private static Path foreignKeystore;
    private static SSLContext originalDefaultContext;

    @BeforeAll
    static void trustTestCertificates() throws Exception {
        originalDefaultContext = SSLContext.getDefault();
        matchingKeystore = generateSelfSignedKeyStore("matching", "localhost");
        foreignKeystore = generateSelfSignedKeyStore("foreign", "foreign-host.invalid");

        // A single trust store with both self-signed certificates: only the first
        // X509TrustManager of the array is honoured by the JDK provider, so splitting them
        // would silently leave the second certificate untrusted.
        KeyStore trustAnchors = KeyStore.getInstance("JKS");
        trustAnchors.load(null, null);
        addTrustAnchor(trustAnchors, matchingKeystore, "matching");
        addTrustAnchor(trustAnchors, foreignKeystore, "foreign");

        TrustManagerFactory trustManagers =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustAnchors);
        SSLContext clientContext = SSLContext.getInstance("TLS");
        clientContext.init(null, trustManagers.getTrustManagers(), null);
        SSLContext.setDefault(clientContext);
    }

    @AfterAll
    static void restoreDefaultSslContext() throws Exception {
        SSLContext.setDefault(originalDefaultContext);
    }

    @Test
    @DisplayName("plain HTTP against a local server is unaffected by the TLS changes")
    void when_get_with_plainHttpLocal_then_ok() throws Exception {
        HttpServer server = startPlainServer();
        try {
            int port = server.getAddress().getPort();

            PinnedHttpClient.PinnedResponse response = PinnedHttpClient.get(
                    new URI("http://localhost:" + port + "/pinned"),
                    InetAddress.getByName("127.0.0.1"), TIMEOUT_MS, TIMEOUT_MS, MAX_BYTES);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(PAYLOAD);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("regression: HTTPS with a certificate whose SAN matches the requested host succeeds")
    void when_get_with_httpsAndMatchingCert_then_ok() throws Exception {
        HttpsServer server = startHttpsServer(matchingKeystore);
        try {
            int port = server.getAddress().getPort();

            PinnedHttpClient.PinnedResponse response = PinnedHttpClient.get(
                    new URI("https://localhost:" + port + "/pinned"),
                    InetAddress.getByName("127.0.0.1"), TIMEOUT_MS, TIMEOUT_MS, MAX_BYTES);

            assertThat(response.status()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(PAYLOAD);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("the certificate is issued for another host, so the handshake must fail")
    void when_get_with_httpsAndMismatchedCert_then_throws() throws Exception {
        HttpsServer server = startHttpsServer(foreignKeystore);
        try {
            int port = server.getAddress().getPort();

            assertThatThrownBy(() -> PinnedHttpClient.get(
                    new URI("https://localhost:" + port + "/pinned"),
                    InetAddress.getByName("127.0.0.1"), TIMEOUT_MS, TIMEOUT_MS, MAX_BYTES))
                    .isInstanceOf(SSLHandshakeException.class)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("localhost")
                    .cause().isInstanceOf(CertificateException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("IP literals are not legal SNI hostnames but hostname verification still applies")
    void when_get_with_httpsAndIpLiteralHost_then_throws() throws Exception {
        HttpsServer server = startHttpsServer(matchingKeystore);
        try {
            int port = server.getAddress().getPort();

            assertThatThrownBy(() -> PinnedHttpClient.get(
                    new URI("https://127.0.0.1:" + port + "/pinned"),
                    InetAddress.getByName("127.0.0.1"), TIMEOUT_MS, TIMEOUT_MS, MAX_BYTES))
                    .isInstanceOf(SSLException.class)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("127.0.0.1")
                    .cause().isInstanceOf(CertificateException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("pinning guard still rejects a peer address other than the pinned one")
    void when_assertRemotePinned_with_divergentAddress_then_throws() throws Exception {
        HttpServer server = startPlainServer();
        try {
            int port = server.getAddress().getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), TIMEOUT_MS);

                assertThatThrownBy(() -> PinnedHttpClient.assertRemotePinned(
                        socket, InetAddress.getByName("127.0.0.2"), port))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("pinned target mismatch");
            }
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startPlainServer() throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/pinned", PinnedHttpClientTlsTest::writeBody);
        server.start();
        return server;
    }

    private static HttpsServer startHttpsServer(Path keystorePath) throws Exception {
        SSLContext serverContext = SSLContext.getInstance("TLS");
        KeyStore keyStore = loadKeyStore(keystorePath);
        KeyManagerFactory keyManagers =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD.toCharArray());
        serverContext.init(keyManagers.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext("/pinned", PinnedHttpClientTlsTest::writeBody);
        server.start();
        return server;
    }

    private static void writeBody(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, PAYLOAD.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(PAYLOAD);
        }
    }

    private static KeyStore loadKeyStore(Path path) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(path)) {
            keyStore.load(in, PASSWORD.toCharArray());
        }
        return keyStore;
    }

    private static void addTrustAnchor(KeyStore trustAnchors, Path keystorePath, String alias)
            throws Exception {
        Certificate certificate = loadKeyStore(keystorePath).getCertificate(alias);
        assertThat(certificate).as("self-signed certificate for " + alias).isNotNull();
        trustAnchors.setCertificateEntry(alias, certificate);
    }

    private static Path generateSelfSignedKeyStore(String alias, String dnsName) throws Exception {
        Path keystore = keystoreDir.resolve(alias + ".p12");
        List<String> command = List.of(
                keytoolExecutable(),
                "-genkeypair",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-dname", "CN=" + dnsName,
                "-ext", "SAN=dns:" + dnsName,
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12",
                "-storepass", PASSWORD,
                "-keypass", PASSWORD);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("keytool timed out for alias " + alias);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed for alias " + alias + ": " + output);
        }
        return keystore;
    }

    private static String keytoolExecutable() {
        Path candidate = Path.of(System.getProperty("java.home"), "bin", "keytool");
        return Files.isExecutable(candidate) ? candidate.toString() : "keytool";
    }
}
