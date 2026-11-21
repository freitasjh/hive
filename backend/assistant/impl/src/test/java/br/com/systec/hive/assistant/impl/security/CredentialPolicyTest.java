package br.com.systec.hive.assistant.impl.security;

import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "OPENAI, sk-test-fake-openai-key",
            "GEMINI, AIzaTestFakeGeminiKey123",
            "ANTHROPIC, sk-ant-test-fake-key",
            "OPENAI_COMPATIBLE, custom-fake-key-1"
    })
    void when_validateUpsert_with_validPrefix_then_noException(AiProvider provider, String apiKey) {
        String baseUrl = provider == AiProvider.OPENAI_COMPATIBLE ? "https://203.0.113.10/v1" : null;

        assertThatNoException().isThrownBy(() -> CredentialPolicy.validateUpsert(provider, apiKey, baseUrl));
    }

    @Test
    void when_validateUpsert_with_wrongPrefix_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENAI, "AIzaWrongPrefix", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formato");
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.GEMINI, "sk-wrong", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.ANTHROPIC, "sk-openai-like", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validateUpsert_with_blankKey_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENAI, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validateUpsert_with_ollamaAndApiKey_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, "sk-should-not-exist", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ollama");
    }

    @Test
    void when_validateUpsert_with_ollamaWithoutKey_then_noException() {
        assertThatNoException().isThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, null, null));
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, null, "http://localhost:11434"));
    }

    @Test
    void when_validateUpsert_with_compatibleWithoutBaseUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENAI_COMPATIBLE, "custom-key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void when_validateUpsert_with_privateBaseUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, null, "http://192.168.1.10:11434"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, null, "http://10.0.0.5:11434"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENAI_COMPATIBLE, "k", "http://172.16.0.9/v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validateUpsert_with_metadataIp_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OLLAMA, null, "http://169.254.169.254/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_validateUpsert_with_publicBaseUrl_then_noException() {
        // 203.0.113.10 = TEST-NET-3: literal público p/ flags do InetAddress, sem depender de DNS.
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENAI_COMPATIBLE, "k", "https://203.0.113.10/v1"));
    }

    @Test
    void when_validateUpsert_with_goZenAnyKey_then_skipsPrefixCheck() {
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_GO, "go-key-without-prefix", null));
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_ZEN, "zen-key-without-prefix", null));
    }

    @Test
    void when_validateUpsert_with_goZenBaseUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_GO, "go-key", "https://custom.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_ZEN, "zen-key", "https://custom.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void when_validateUpsert_with_goZenEchoOfFixedVendorUrl_then_noException() {
        // O valor é público, fixo e já exibido readonly na UI: ecoar não é override.
        assertThatNoException().isThrownBy(() -> CredentialPolicy.validateUpsert(
                AiProvider.OPENCODE_GO, "go-key", "https://opencode.ai/zen/go/v1"));
        assertThatNoException().isThrownBy(() -> CredentialPolicy.validateUpsert(
                AiProvider.OPENCODE_ZEN, "zen-key", "https://opencode.ai/zen/v1"));
        assertThatNoException().isThrownBy(() -> CredentialPolicy.validateUpsert(
                AiProvider.OPENCODE_GO, "go-key", "  https://opencode.ai/zen/go/v1  "));
    }

    @Test
    void when_validateUpsert_with_goZenOverrideAttempt_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(
                AiProvider.OPENCODE_GO, "go-key", "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(
                AiProvider.OPENCODE_ZEN, "zen-key", "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");
    }

    @Test
    void when_validateUpsert_with_goZenWithoutBaseUrl_then_noException() {
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_GO, "go-key", null));
        assertThatNoException().isThrownBy(
                () -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_ZEN, "zen-key", "  "));
    }

    @Test
    void when_validateUpsert_with_goZenBlankKey_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_GO, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CredentialPolicy.validateUpsert(AiProvider.OPENCODE_ZEN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
