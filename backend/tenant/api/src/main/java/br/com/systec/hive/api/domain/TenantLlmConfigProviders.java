package br.com.systec.hive.api.domain;

import br.com.systec.hive.commons.api.domain.BaseDomain;
import br.com.systec.hive.provider.api.AiProvider;

import java.io.Serial;
import java.util.Set;

/**
 * Which providers a tenant may name as its default LLM. An <b>allowlist</b>, not a negation.
 *
 * <p>Same rule as {@code PROVIDERS_WITH_TENANT_BASE_URL} in the frontend: a provider enters here by
 * an explicit decision, never by being absent from a denylist. A denylist fails open — the next
 * member added to {@link AiProvider} would be accepted by the product the day it is compiled, with
 * nobody having decided anything.
 *
 * <p>{@code Set.of} also freezes the set, so the guarantee survives a careless
 * {@code ALLOWED.add(...)} in a future change the same way a fixed array would not.
 */
public final class TenantLlmConfigProviders {

    private static final Set<AiProvider> ALLOWED = Set.of(
            AiProvider.OPENAI,
            AiProvider.GEMINI,
            AiProvider.ANTHROPIC,
            AiProvider.OLLAMA,
            AiProvider.OPENAI_COMPATIBLE,
            AiProvider.OPENCODE_GO,
            AiProvider.OPENCODE_ZEN);

    private TenantLlmConfigProviders() {
    }

    public static boolean isAllowed(AiProvider provider) {
        return provider != null && ALLOWED.contains(provider);
    }

    public static Set<AiProvider> allowed() {
        return ALLOWED;
    }
}
