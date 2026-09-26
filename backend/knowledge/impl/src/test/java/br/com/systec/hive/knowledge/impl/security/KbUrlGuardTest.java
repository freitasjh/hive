package br.com.systec.hive.knowledge.impl.security;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbUrlGuardTest {

    @Test
    void when_validate_with_loopback_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("http://127.0.0.1/docs"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_localhost_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("http://localhost:80/x"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_metadataIp_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("http://169.254.169.254/latest"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_privateHost_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("http://192.168.1.10/x"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_disallowedPort_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("http://example.com:8080/x"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_ftpScheme_then_blocked() {
        assertThatThrownBy(() -> KbUrlGuard.validateInitial("ftp://example.com/x"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_validate_with_publicHost_then_ok() {
        URI uri = KbUrlGuard.validateInitial("https://example.com/docs/page");
        assertThat(uri.getHost()).isEqualTo("example.com");
    }

    @Test
    void when_guard_with_cgnatStart_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("100.64.0.1"), "cgnat"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_guard_with_cgnatMiddle_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("100.100.5.5"), "cgnat"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_guard_with_cgnatEnd_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("100.127.255.254"), "cgnat"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_guard_with_outsideCgnat_then_allowed() throws Exception {
        KbUrlGuard.assertPublicAddress(InetAddress.getByName("100.128.0.1"), "public");
        KbUrlGuard.assertPublicAddress(InetAddress.getByName("100.63.255.255"), "public");
    }

    @Test
    void when_guard_with_ipv6Loopback_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("::1"), "v6"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_guard_with_ipv6LinkLocal_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("fe80::1"), "v6"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_guard_with_ipv6UniqueLocal_then_blocked() throws Exception {
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("fc00::1"), "v6"))
                .isInstanceOf(KbInvalidUrlException.class);
        assertThatThrownBy(() -> KbUrlGuard.assertPublicAddress(InetAddress.getByName("fd12:3456::1"), "v6"))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_fetch_with_redirectToPrivate_then_blocked() throws Exception {
        KbUrlGuard.RedirectFetcher fetcher = (uri, ip) ->
                new KbUrlGuard.RedirectAwareContent(new byte[0], null, "http://127.0.0.1/evil");
        assertThatThrownBy(() -> KbUrlGuard.fetchWithRedirects("https://example.com/a", fetcher))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_fetch_with_tooManyRedirects_then_blocked() throws Exception {
        KbUrlGuard.RedirectFetcher fetcher = (uri, ip) ->
                new KbUrlGuard.RedirectAwareContent(new byte[0], null, "https://example.com/next");
        assertThatThrownBy(() -> KbUrlGuard.fetchWithRedirects("https://example.com/a", fetcher))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_fetch_with_binaryMagic_then_blocked() throws Exception {
        byte[] elf = new byte[]{0x7f, 'E', 'L', 'F', 0x01};
        KbUrlGuard.RedirectFetcher fetcher = (uri, ip) ->
                new KbUrlGuard.RedirectAwareContent(elf, "text/plain", null);
        assertThatThrownBy(() -> KbUrlGuard.fetchWithRedirects("https://example.com/a", fetcher))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_fetch_with_validText_then_ok() throws Exception {
        byte[] body = "hello knowledge".getBytes();
        KbUrlGuard.RedirectFetcher fetcher = (uri, ip) ->
                new KbUrlGuard.RedirectAwareContent(body, "text/plain", null);
        KbUrlGuard.FetchedContent content =
                KbUrlGuard.fetchWithRedirects("https://example.com/a", fetcher);
        assertThat(content.bytes()).isEqualTo(body);
        assertThat(content.finalUrl()).isEqualTo("https://example.com/a");
    }
}
