package br.com.systec.hive.knowledge.impl.security;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbUrlGuardRedirectTest {

    @Test
    void when_fetch_with_redirectToInternal_then_blocked() {
        KbUrlGuard.RedirectFetcher fetcher = (uri, pinned) -> new KbUrlGuard.RedirectAwareContent(
                new byte[0], null, "http://127.0.0.1/evil");

        assertThatThrownBy(() -> KbUrlGuard.fetchWithRedirects("http://93.184.216.34/start", fetcher))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_fetch_with_relativeRedirect_then_followsAndReturnsFinalUrl() {
        AtomicInteger calls = new AtomicInteger();
        KbUrlGuard.RedirectFetcher fetcher = (uri, pinned) -> {
            if (calls.getAndIncrement() == 0) {
                return new KbUrlGuard.RedirectAwareContent(new byte[0], "text/plain", "/next");
            }
            return new KbUrlGuard.RedirectAwareContent("ok".getBytes(), "text/plain", null);
        };

        KbUrlGuard.FetchedContent content =
                KbUrlGuard.fetchWithRedirects("http://93.184.216.34/start", fetcher);

        assertThat(new String(content.bytes())).isEqualTo("ok");
        assertThat(content.finalUrl()).isEqualTo("http://93.184.216.34/next");
    }

    @Test
    void when_fetch_with_tooManyRedirects_then_blocked() {
        KbUrlGuard.RedirectFetcher fetcher =
                (uri, pinned) -> new KbUrlGuard.RedirectAwareContent(new byte[0], null, "/loop");

        assertThatThrownBy(() -> KbUrlGuard.fetchWithRedirects("http://93.184.216.34/start", fetcher))
                .isInstanceOf(KbInvalidUrlException.class);
    }

    @Test
    void when_pinnedAddress_with_uriAndIp_then_matchesPinnedTarget() throws Exception {
        InetAddress pinned = InetAddress.getByName("93.184.216.34");

        assertThat(KbUrlGuard.pinnedAddress(new URI("https://example.com/x"), pinned).getPort()).isEqualTo(443);
        assertThat(KbUrlGuard.pinnedAddress(new URI("https://example.com/x"), pinned).getAddress())
                .isEqualTo(pinned);
    }
}
