package br.com.systec.hive.provider.api.security;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;

import java.net.InetAddress;
import java.net.URI;

/**
 * Shared SSRF guard for tenant-controlled LLM base URLs (Ollama, OpenAI-compatible).
 * Official vendor endpoints are fixed constants; only custom base URLs pass through here.
 * Loopback is allowed (local Ollama default, draft exception); site-local/link-local/any-local/multicast
 * and CGNAT 100.64.0.0/10 are blocked. Non-loopback hosts require https.
 */
public final class ProviderBaseUrlGuard {

    private ProviderBaseUrlGuard() {
    }

    /**
     * Providers whose base URL is a fixed vendor constant (OpenCode GO/Zen). The value is public,
     * immutable and already displayed as read-only by the UIs, so a client that echoes it is not
     * attempting an override — only a <em>different</em> value is, and that stays blocked.
     * <p>
     * No-op when the provider has no fixed vendor URL or when no URL was sent.
     */
    public static void validateFixedVendorBaseUrlEcho(AiProvider provider, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        String fixed = VendorCatalog.baseUrlFor(provider);
        if (fixed == null) {
            return;
        }
        if (!fixed.equals(baseUrl.trim())) {
            throw new IllegalArgumentException("baseUrl é fixa do vendor para " + provider + "; não envie baseUrl.");
        }
    }

    public static void validate(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("baseUrl inválida: " + baseUrl + ".");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("baseUrl deve usar http ou https.");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("baseUrl não pode conter credenciais.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("baseUrl inválida: host ausente.");
        }
        boolean loopback = isLoopbackHost(host);
        if (!loopback && !scheme.equals("https")) {
            throw new IllegalArgumentException("baseUrl remota deve usar https.");
        }
        assertPublicHost(host);
    }

    private static boolean isLoopbackHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!address.isLoopbackAddress()) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            throw new IllegalArgumentException("baseUrl com host não resolvível: " + host + ".");
        }
    }

    private static void assertPublicHost(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || isCarrierGradeNat(address)) {
                    throw new IllegalArgumentException(
                            "baseUrl aponta para endereço interno, bloqueado por segurança: " + host + ".");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("baseUrl com host não resolvível: " + host + ".");
        }
    }

    public static boolean isCarrierGradeNat(InetAddress address) {
        byte[] raw = address.getAddress();
        return raw.length == 4 && (raw[0] & 0xFF) == 100 && (raw[1] & 0xFF) >= 64 && (raw[1] & 0xFF) <= 127;
    }
}
