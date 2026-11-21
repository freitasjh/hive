package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;

import java.util.List;

/**
 * Uma linha do catálogo local que alimenta o seletor da UI.
 *
 * <p>Sem segredo: nem apiKey, nem baseUrl, nem last4. Apenas a lista de modelos, o default e se o
 * tenant já tem credencial para aquele vendor — o que a tela precisa para decidir o que oferecer.
 */
public record ProviderCatalogEntryDTO(
        AiProvider provider,
        List<String> models,
        String defaultModel,
        boolean configured
) {
    public static ProviderCatalogEntryDTO of(AiProvider provider, boolean configured) {
        return new ProviderCatalogEntryDTO(provider, VendorCatalog.modelsFor(provider),
                VendorCatalog.defaultModelFor(provider), configured);
    }
}
