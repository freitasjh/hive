package br.com.systec.hive.knowledge.impl.security;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class KbUrlGuard {

    public static final int MAX_REDIRECTS = 3;
    public static final long MAX_BYTES = 2L * 1024L * 1024L;
    public static final int CONNECT_TIMEOUT_MS = 5000;
    public static final int TOTAL_TIMEOUT_MS = 10000;
    public static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443);

    private static final Set<String> DOCS_RANGES = Set.of("203.0.113.", "198.51.100.", "192.0.2.");

    private KbUrlGuard() {
    }

    public record FetchedContent(byte[] bytes, String contentType, String finalUrl) {
    }

    public interface UrlFetcher {
        FetchedContent fetch(URI uri, InetAddress pinnedIp) throws Exception;
    }

    public static URI validateInitial(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new KbInvalidUrlException("empty");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new KbInvalidUrlException("malformed");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new KbInvalidUrlException("scheme must be http/https");
        }
        if (uri.getUserInfo() != null) {
            throw new KbInvalidUrlException("credentials in URL");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new KbInvalidUrlException("missing host");
        }
        int port = uri.getPort() == -1 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
        if (!ALLOWED_PORTS.contains(port)) {
            throw new KbInvalidUrlException("port not allowed (80/443 only)");
        }
        assertPublicHost(host);
        return uri;
    }

    public record RedirectAwareContent(byte[] bytes, String contentType, String location) {
    }

    /**
     * Pinning model: the fetcher must open the TCP/TLS connection against the pinned IP
     * literal (see {@code KbIngestionService.openPinnedConnection}), never by re-resolving
     * the hostname, preserving the original host via {@code Host} header + SNI. Post-connect
     * re-resolution is intentionally NOT used here — it reintroduces the TOCTOU window it
     * claims to check.
     */
    public static FetchedContent fetchWithRedirects(String rawUrl, RedirectFetcher fetcher) {
        URI current = validateInitial(rawUrl);
        InetAddress pinned = resolvePinned(current.getHost());
        int hops = 0;
        while (true) {
            RedirectAwareContent content;
            try {
                content = fetcher.fetch(current, pinned);
            } catch (KbInvalidUrlException e) {
                throw e;
            } catch (Exception e) {
                throw new KbInvalidUrlException("fetch failed");
            }
            if (content.location() == null) {
                assertSize(content.bytes());
                assertContentType(content.contentType(), content.bytes());
                return new FetchedContent(content.bytes(), content.contentType(), current.toString());
            }
            hops++;
            if (hops > MAX_REDIRECTS) {
                throw new KbInvalidUrlException("too many redirects");
            }
            current = current.resolve(content.location());
            validateInitial(current.toString());
            pinned = resolvePinned(Objects.requireNonNull(current.getHost()));
        }
    }

    public interface RedirectFetcher {
        RedirectAwareContent fetch(URI uri, InetAddress pinnedIp) throws Exception;
    }

    static InetAddress resolvePinned(String host) {
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            if (all.length == 0) {
                throw new KbInvalidUrlException("unresolvable host");
            }
            InetAddress pinned = all[0];
            assertPublicAddress(pinned, host);
            return pinned;
        } catch (KbInvalidUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new KbInvalidUrlException("unresolvable host");
        }
    }

    static void assertPublicHost(String host) {        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                assertPublicAddress(address, host);
            }
        } catch (KbInvalidUrlException e) {
            throw e;
        } catch (Exception e) {
            throw new KbInvalidUrlException("unresolvable host");
        }
    }

    public static void assertPublicAddress(InetAddress address, String host) {
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            throw new KbInvalidUrlException("internal address blocked: " + host);
        }
        String ip = address.getHostAddress();
        if (ip != null) {
            for (String prefix : DOCS_RANGES) {
                if (ip.startsWith(prefix)) {
                    throw new KbInvalidUrlException("documentation range blocked: " + host);
                }
            }
            if (ip.startsWith("100.")) {
                int second;
                try {
                    second = Integer.parseInt(ip.split("\\.")[1]);
                } catch (Exception e) {
                    second = -1;
                }
                if (second >= 64 && second <= 127) {
                    throw new KbInvalidUrlException("CGNAT range blocked: " + host);
                }
            }
            if (ip.contains(":")) {
                assertPublicIpv6(ip, host);
            }
            if (ip.equals("169.254.169.254")) {
                throw new KbInvalidUrlException("metadata endpoint blocked: " + host);
            }
        }
    }

    static void assertPublicIpv6(String ip, String host) {
        String v6 = ip.toLowerCase(Locale.ROOT);
        if (v6.equals("::1") || v6.equals("::ffff:127.0.0.1")
                || v6.startsWith("fe80:") || v6.startsWith("fec0:")
                || v6.startsWith("fc") || v6.startsWith("fd")
                || v6.startsWith("ff")) {
            throw new KbInvalidUrlException("internal IPv6 address blocked: " + host);
        }
    }

    static void assertSize(byte[] bytes) {
        if (bytes != null && bytes.length > MAX_BYTES) {
            throw new KbInvalidUrlException("content exceeds 2MB");
        }
    }

    static void assertContentType(String contentType, byte[] bytes) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        boolean allowedType = ct.contains("text/") || ct.contains("markdown")
                || ct.contains("pdf") || ct.contains("html") || ct.isBlank();
        if (!allowedType) {
            throw new KbInvalidUrlException("content-type not allowed: " + contentType);
        }
        if (bytes != null && bytes.length >= 4) {
            if (bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
                throw new KbInvalidUrlException("binary content blocked (magic-bytes)");
            }
            if (bytes[0] == 'M' && bytes[1] == 'Z') {
                throw new KbInvalidUrlException("binary content blocked (magic-bytes)");
            }
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static InetSocketAddress pinnedAddress(URI uri, InetAddress pinnedIp) {
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        return new InetSocketAddress(pinnedIp, port);
    }

    public static void requireOkStatus(int status) {
        if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) {
            return;
        }
        if (status < 200 || status >= 300) {
            throw new KbInvalidUrlException("unexpected status " + status);
        }
    }

    public static List<String> allowedContentHints() {
        return List.of("text/plain", "text/markdown", "text/html", "application/pdf");
    }
}
