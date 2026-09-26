package br.com.systec.hive.commons.impl.http;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Minimal HTTP/1.1 client that connects to a pre-validated (pinned) IP literal.
 *
 * <p>Pure infrastructure: no domain dependency. Closes the DNS-rebinding/TOCTOU window:
 * the TCP {@link Socket} is opened directly against {@code pinnedIp}, so no DNS
 * re-resolution happens at connect time. The original hostname is preserved for
 * virtual-host routing via the {@code Host} header and for TLS via SNI plus endpoint
 * identification (RFC 2818) enforced by the TLS provider during the handshake against the
 * original host. The peer address is asserted via {@link Socket#getRemoteSocketAddress()}
 * before the body is read.
 *
 * <p>Residual risks: JVM-wide {@code ProxySelector} is bypassed (direct socket, no proxy
 * support by design); TLS trust relies on the JVM default trust store.
 */
public final class PinnedHttpClient {

    public static final String USER_AGENT = "HiveAI-KB/1.0";

    private PinnedHttpClient() {
    }

    public record PinnedResponse(int status, Map<String, List<String>> headers, byte[] body) {
    }

    public static PinnedResponse get(URI uri, InetAddress pinnedIp, int connectTimeoutMs,
            int readTimeoutMs, long maxBytes) throws IOException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IOException("scheme must be http/https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IOException("missing host");
        }
        int port = uri.getPort() == -1 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String target = uri.getRawQuery() == null ? rawPath : rawPath + "?" + uri.getRawQuery();

        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(pinnedIp, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        try {
            if ("https".equals(scheme)) {
                socket = wrapTls(socket, host, port);
                socket.setSoTimeout(readTimeoutMs);
            }
            assertRemotePinned(socket, pinnedIp, port);
            String hostHeader = uri.getPort() == -1 || uri.getPort() == port && isDefaultPort(scheme, port)
                    ? host
                    : host + ":" + port;
            String request = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return readResponse(socket.getInputStream(), maxBytes);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    static boolean isDefaultPort(String scheme, int port) {
        return ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
    }

    static Socket wrapTls(Socket socket, String sniHost, int port) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(socket, sniHost, port, true);
        SSLParameters params = ssl.getSSLParameters();
        // Hostname verification must happen inside the handshake: the provider validates the
        // certificate chain AND the peer identity against sniHost, failing with
        // SSLHandshakeException. HttpsURLConnection.getDefaultHostnameVerifier() cannot be used
        // here (it is an always-false stub in the JDK) and
        // HttpsURLConnection.setDefaultHostnameVerifier(...) is JVM-global state, so both are
        // deliberately avoided. Deliberately not guarded: if a provider ever rejected the
        // algorithm, failing loudly is safer than silently skipping identity verification.
        params.setEndpointIdentificationAlgorithm("HTTPS");
        try {
            params.setServerNames(List.of(new SNIHostName(sniHost)));
        } catch (IllegalArgumentException ignored) {
            // IP literals are not legal SNI hostnames; Host header still routes correctly.
        }
        ssl.setSSLParameters(params);
        ssl.startHandshake();
        return ssl;
    }

    public static void assertRemotePinned(Socket socket, InetAddress pinnedIp, int port) throws IOException {
        InetSocketAddress expected = new InetSocketAddress(pinnedIp, port);
        if (!(socket.getRemoteSocketAddress() instanceof InetSocketAddress actual)
                || !actual.getAddress().equals(expected.getAddress())
                || actual.getPort() != expected.getPort()) {
            throw new IOException("pinned target mismatch: connected=" + socket.getRemoteSocketAddress());
        }
    }

    public static PinnedResponse readResponse(InputStream in, long maxBytes) throws IOException {
        String statusLine = readLine(in);
        if (statusLine == null || !statusLine.startsWith("HTTP/")) {
            throw new IOException("bad status line");
        }
        String[] parts = statusLine.split(" ", 3);
        int status;
        try {
            status = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            throw new IOException("bad status code");
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            }
        }
        byte[] body;
        if (isChunked(headers)) {
            body = readChunked(in, maxBytes);
        } else {
            body = readToClose(in, maxBytes);
        }
        return new PinnedResponse(status, headers, body);
    }

    static boolean isChunked(Map<String, List<String>> headers) {
        List<String> values = headers.get("transfer-encoding");
        if (values == null) {
            return false;
        }
        for (String v : values) {
            if (v.toLowerCase(Locale.ROOT).contains("chunked")) {
                return true;
            }
        }
        return false;
    }

    static byte[] readChunked(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(in);
            if (sizeLine == null) {
                throw new EOFException("truncated chunk");
            }
            int semicolon = sizeLine.indexOf(';');
            String hex = (semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim();
            int size;
            try {
                size = Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("bad chunk size");
            }
            if (size == 0) {
                readLine(in);
                return buffer.toByteArray();
            }
            copyExact(in, buffer, size, maxBytes);
            readLine(in);
        }
    }

    static byte[] readToClose(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            if ((long) buffer.size() + read > maxBytes) {
                throw new IOException("content exceeds limit");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    static void copyExact(InputStream in, ByteArrayOutputStream buffer, int size, long maxBytes)
            throws IOException {
        byte[] chunk = new byte[8192];
        int remaining = size;
        while (remaining > 0) {
            int read = in.read(chunk, 0, Math.min(chunk.length, remaining));
            if (read == -1) {
                throw new EOFException("truncated chunk body");
            }
            if ((long) buffer.size() + read > maxBytes) {
                throw new IOException("content exceeds limit");
            }
            buffer.write(chunk, 0, read);
            remaining -= read;
        }
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int prev = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (prev == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.ISO_8859_1);
            }
            if (current == '\n' && prev == -1 && line.size() == 0) {
                return "";
            }
            line.write(current);
            prev = current;
        }
        if (line.size() == 0) {
            return null;
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }
}
