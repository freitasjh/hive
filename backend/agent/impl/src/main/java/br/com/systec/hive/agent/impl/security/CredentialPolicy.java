package br.com.systec.hive.agent.impl.security;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.api.security.ProviderBaseUrlGuard;

public final class CredentialPolicy {

    private CredentialPolicy() {
    }

    public static void validateUpsert(AiProvider provider, String apiKey, String baseUrl) {
        if (provider == null) {
            throw new IllegalArgumentException("provider é obrigatório.");
        }
        if (provider == AiProvider.OLLAMA) {
            if (apiKey != null && !apiKey.isBlank()) {
                throw new IllegalArgumentException("Ollama não utiliza apiKey; não envie chave.");
            }
        } else {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey é obrigatória para " + provider + ".");
            }
            assertPrefix(provider, apiKey.trim());
        }
        if (provider == AiProvider.OPENAI_COMPATIBLE && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalArgumentException("baseUrl é obrigatória para OPENAI_COMPATIBLE.");
        }
        if (VendorCatalog.baseUrlFor(provider) != null) {
            // GO/Zen: a baseUrl é constante do vendor, não input do tenant — tolera só o eco do valor
            // público que a UI exibe (evita o footgun de API) e não a submete ao guard SSRF.
            ProviderBaseUrlGuard.validateFixedVendorBaseUrlEcho(provider, baseUrl);
        } else {
            ProviderBaseUrlGuard.validate(baseUrl);
        }
    }

    private static void assertPrefix(AiProvider provider, String apiKey) {
        boolean valid = switch (provider) {
            case OPENAI -> apiKey.startsWith("sk-");
            case GEMINI -> apiKey.startsWith("AIza");
            case ANTHROPIC -> apiKey.startsWith("sk-ant-");
            case OPENAI_COMPATIBLE, OPENCODE_GO -> true;
            case OPENCODE_ZEN -> true;
            case OLLAMA -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "apiKey em formato inválido para " + provider + ".");
        }
    }
}
