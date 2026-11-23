package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.service.TurnPolicyResolver;
import br.com.systec.hive.assistant.api.util.ProviderOverrideGuard;
import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.provider.api.AiProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The provider and the model a turn runs with, read once per turn from the tenant's single row.
 *
 * <p>The provider and the model are a property of the tenant, not of the conversation: there is
 * exactly one row, read through {@link TenantLlmConfigService#require}, which throws rather than
 * falling back. Both used to be read off the agent, and the two readings had different lifetimes — a
 * prompt that could differ per conversation and a model that could not — which is the whole reason
 * the agent had to exist.
 *
 * <h3>What this class is for</h3>
 *
 * <p>It answers one question with three steps that must happen in one order, and the order is the
 * part worth owning:
 *
 * <ol>
 *   <li>read the tenant's row — absent throws {@code TenantLlmConfigNotFoundException} (422);</li>
 *   <li>validate the pair against the vendor catalog — unsupported throws (400)</li>
 *   <li>resolve the credential for that vendor — absent throws {@code ConfigMissingException} (422)</li>
 * </ol>
 *
 * <p>The order is load-bearing: a tenant whose model is not in the catalog gets the 400 about the
 * model, not the 422 about the credential, because asking for a credential the vendor will never be
 * asked to use produces a message about the wrong screen. And nothing is asked of the credential
 * store when the catalog already refused — which is what keeps a typo in the model field from
 * reading (and possibly creating) a credential row.
 *
 * <p>Since 2026-11-20 a conversation may carry its own pair (persisted in {@code chat_session}, V7).
 * That is the override overload below: it skips the tenant row entirely and applies the same three
 * steps to the chosen pair. "No override" is still exactly the path described above — the tenant
 * default is untouched, and this class never writes it.
 *
 * <p>It lives in {@code impl} rather than in {@code api} for a build reason, not a design
 * preference: {@link CredentialResolver} reads the encrypted credential repository, and
 * {@code api → impl} would be a Maven cycle. The <em>policy</em> this class carries is the order
 * above, and that is now stated once instead of being three inline statements inside a turn.
 *
 * @see Resolved
 */
@Service
public class ProviderResolution implements TurnPolicyResolver {

    private final TenantLlmConfigService tenantLlmConfigService;
    private final CredentialResolver credentialResolver;

    public ProviderResolution(TenantLlmConfigService tenantLlmConfigService,
                              CredentialResolver credentialResolver) {
        this.tenantLlmConfigService = tenantLlmConfigService;
        this.credentialResolver = credentialResolver;
    }

    /**
     * Everything the turn needs in order to call a provider, for one tenant.
     *
     * <p>Throws rather than returning a partial answer: a missing configuration is a message to the
     * operator about which screen to visit, and a silent default would turn it into a conversation
     * answered by a model nobody chose and nobody is paying for.
     */
    @Override
    public Resolved resolve(UUID tenantId) {
        return resolve(tenantId, null, null);
    }

    /**
     * Resolve the pair for one turn, with an optional per-conversation override.
     *
     * <p>The override is optional and ATOMIC: both fields or neither. A tenant default is read only
     * when there is no override — the tenant's row is not touched when the conversation chose its own
     * pair. The UI sends the chosen model in the request body (official source consulted 2026-11-20:
     * https://ui.shadcn.com/docs/ai-elements/base/model-selector), so this is the boundary where that
     * choice becomes a validated provider/model pair.
     *
     * <p>The order below is load-bearing and identical to the default path: validate the pair against
     * the catalog (400) BEFORE resolving the credential (422). A typo in the model must never read —
     * and possibly create — a credential row for a vendor that would never be asked to use it.
     */
    @Override
    public Resolved resolve(UUID tenantId, AiProvider providerOverride, String modelOverride) {
        AiProvider provider;
        String model;

        if (providerOverride == null && modelOverride == null) {
            // `require` throws TenantLlmConfigNotFoundException (422) when the tenant has not configured
            // them: there is deliberately no fallback.
            TenantLlmConfig llmConfig = tenantLlmConfigService.require(tenantId);
            provider = llmConfig.getProvider();
            model = llmConfig.getModel();
        } else {
            provider = providerOverride;
            model = modelOverride;
        }

        // Uma única regra para o par, venha ele do tenant ou da conversa. O guard recusa o par
        // pela metade e valida o modelo contra o catálogo ANTES da credencial ser lida.
        ProviderOverrideGuard.validate(provider, model);

        // Ponte credential->chat: resolve (tenant, vendor) -> apiKey/baseUrl; ausente => 422 CONFIG_MISSING.
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(tenantId, provider);

        return new Resolved(provider, model, credential.apiKey(), credential.baseUrl());
    }

}
