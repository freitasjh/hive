package br.com.systec.hive.provider.api.security;

import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderBaseUrlGuardTest {

    @Test
    void when_validate_with_loopbackHttp_then_noException() {
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate("http://localhost:11434"));
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate("http://127.0.0.1:11434"));
    }

    @Test
    void when_validate_with_publicHttps_then_noException() {
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate("https://203.0.113.10/v1"));
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate("https://opencode.ai/zen/go"));
    }

    @Test
    void when_validate_with_carrierGradeNat_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("https://100.64.0.1:11434"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interno");
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("https://100.100.5.5/v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("https://100.127.255.255/v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validate_with_remoteHttp_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("http://203.0.113.10/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void when_validate_with_privateRanges_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("https://192.168.1.10:11434"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("https://10.0.0.5/v1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderBaseUrlGuard.validate("http://169.254.169.254/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validate_with_blank_then_noException() {
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate(null));
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard.validate("  "));
    }

    @Test
    void when_validateFixedVendorBaseUrlEcho_with_exactVendorUrl_then_noException() {
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_GO, "https://opencode.ai/zen/go/v1"));
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_ZEN, "https://opencode.ai/zen/v1"));
    }

    @Test
    void when_validateFixedVendorBaseUrlEcho_with_paddedVendorUrl_then_noException() {
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_GO, "  https://opencode.ai/zen/go/v1  "));
    }

    @Test
    void when_validateFixedVendorBaseUrlEcho_with_otherUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_GO, "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");
        assertThatThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_ZEN, "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        // sem "/v1" não é a constante do vendor (o LangChain4j exige o segmento) → continua bloqueio
        assertThatThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_GO, "https://opencode.ai/zen/go"))
                .isInstanceOf(IllegalArgumentException.class);
        // barra final também não é a constante
        assertThatThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_ZEN, "https://opencode.ai/zen/v1/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validateFixedVendorBaseUrlEcho_with_blankOrTenantOwnedProvider_then_noException() {
        assertThatNoException().isThrownBy(
                () -> ProviderBaseUrlGuard.validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_GO, null));
        assertThatNoException().isThrownBy(
                () -> ProviderBaseUrlGuard.validateFixedVendorBaseUrlEcho(AiProvider.OPENCODE_ZEN, "  "));
        assertThatNoException().isThrownBy(
                () -> ProviderBaseUrlGuard.validateFixedVendorBaseUrlEcho(AiProvider.OLLAMA, "https://evil.example.com"));
        assertThatNoException().isThrownBy(() -> ProviderBaseUrlGuard
                .validateFixedVendorBaseUrlEcho(AiProvider.OPENAI_COMPATIBLE, "https://203.0.113.10/v1"));
    }
}
