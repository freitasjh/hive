package br.com.systec.hive.provider.api;

import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderCatalogTest {

    @Test
    void when_validate_with_supportedModel_then_noException() {
        VendorCatalog.validate(AiProvider.OPENAI, "gpt-4o-mini");
    }

    @Test
    void when_validate_with_modelFromAnotherVendor_then_throwsModelNotSupported() {
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENAI, "llama3.2"))
                .isInstanceOf(ModelNotSupportedForProviderException.class)
                .hasMessageContaining("llama3.2")
                .hasMessageContaining("OPENAI");
    }

    @Test
    void when_validate_with_compatibleProviderAndFreeModel_then_noException() {
        VendorCatalog.validate(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5");
        VendorCatalog.validate(AiProvider.OPENAI_COMPATIBLE, "any-custom-model-xyz");
    }

    @Test
    void when_validate_with_blankModel_then_throwsModelNotSupported() {
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENAI, " "))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENAI_COMPATIBLE, null))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
    }

    @Test
    void when_validate_with_nullProvider_then_throwsModelNotSupported() {
        assertThatThrownBy(() -> VendorCatalog.validate(null, "gpt-4o-mini"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
    }

    @Test
    void when_defaultModelFor_with_eachVendor_then_returnsExpectedDefault() {
        assertThat(VendorCatalog.defaultModelFor(AiProvider.OPENAI)).isEqualTo("gpt-4o-mini");
        assertThat(VendorCatalog.defaultModelFor(AiProvider.GEMINI)).isEqualTo("gemini-2.0-flash");
        assertThat(VendorCatalog.defaultModelFor(AiProvider.ANTHROPIC)).isEqualTo("claude-3-5-haiku-latest");
        assertThat(VendorCatalog.defaultModelFor(AiProvider.OLLAMA)).isEqualTo("llama3.2");
        assertThat(VendorCatalog.defaultModelFor(AiProvider.OPENAI_COMPATIBLE)).isEqualTo("mimo-v2.5");
    }

    @Test
    void when_modelsFor_with_vendor_then_containsDefault() {
        for (AiProvider provider : AiProvider.values()) {
            assertThat(VendorCatalog.modelsFor(provider))
                    .contains(VendorCatalog.defaultModelFor(provider));
        }
    }

    @Test
    void when_defaultModelFor_with_goZen_then_returnsLiveDefaults() {
        assertThat(VendorCatalog.defaultModelFor(AiProvider.OPENCODE_GO)).isEqualTo("mimo-v2.5");
        assertThat(VendorCatalog.defaultModelFor(AiProvider.OPENCODE_ZEN)).isEqualTo("claude-sonnet-4-5");
    }

    @Test
    void when_baseUrlFor_with_goZen_then_returnsFixedVendorUrls() {
        assertThat(VendorCatalog.baseUrlFor(AiProvider.OPENCODE_GO)).isEqualTo("https://opencode.ai/zen/go/v1");
        assertThat(VendorCatalog.baseUrlFor(AiProvider.OPENCODE_ZEN)).isEqualTo("https://opencode.ai/zen/v1");
        assertThat(VendorCatalog.baseUrlFor(AiProvider.OPENAI)).isNull();
        assertThat(VendorCatalog.baseUrlFor(AiProvider.OPENAI_COMPATIBLE)).isNull();
    }

    @Test
    void when_baseUrlFor_with_goZen_then_includesSingleV1SegmentRequiredByOpenAiCompatibleClients() {
        // Contrato: o LangChain4j (OpenAiChatModel) concatena apenas "/chat/completions" ao
        // baseUrl. Sem o "/v1" a chamada cai em 404 na landing page do opencode.ai.
        for (String baseUrl : List.of(VendorCatalog.OPENCODE_GO_BASE_URL, VendorCatalog.OPENCODE_ZEN_BASE_URL)) {
            assertThat(baseUrl).endsWith("/v1");
            assertThat(baseUrl).doesNotContain("/v1/v1");
        }
    }

    @Test
    void when_validate_with_goZenLiveModels_then_noException() {
        VendorCatalog.validate(AiProvider.OPENCODE_GO, "mimo-v2.5");
        VendorCatalog.validate(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5");
        VendorCatalog.validate(AiProvider.OPENCODE_GO, "kimi-k2.5");
    }

    @Test
    void when_validate_with_denylistedResponsesModels_then_throwsModelNotSupported() {
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENCODE_GO, "grok-4.5"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENCODE_GO, "gpt-5.6-luna"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENCODE_ZEN, "muse-spark-1.3-contributor"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
    }

    @Test
    void when_validate_with_denylistedMessagesModels_then_throwsModelNotSupported() {
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENCODE_GO, "minimax-m2.5"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        assertThatThrownBy(() -> VendorCatalog.validate(AiProvider.OPENCODE_ZEN, "qwen3.8-max"))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
    }

    @Test
    void when_isDenylisted_with_liveModels_then_returnsFalse() {
        assertThat(VendorCatalog.isDenylisted("mimo-v2.5")).isFalse();
        assertThat(VendorCatalog.isDenylisted("claude-sonnet-4-5")).isFalse();
        assertThat(VendorCatalog.isDenylisted("kimi-k2.5")).isFalse();
        assertThat(VendorCatalog.isDenylisted(null)).isFalse();
    }
}
