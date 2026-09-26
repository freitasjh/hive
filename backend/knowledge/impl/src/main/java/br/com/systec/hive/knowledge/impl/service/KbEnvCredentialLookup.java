package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.port.KbCredentialLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Standalone fallback for the knowledge module when the agent module (with its
 * per-tenant {@code CredentialResolver}) is not on the classpath. In the full
 * application the {@code KbTenantCredentialAdapter} bean (marked
 * {@code @Primary}) takes precedence and resolves credentials per
 * (tenant, provider) from the database, so this bean is never used there.
 *
 * <p>Note: env-based credentials are global, not tenant-scoped. Do not rely on
 * this bean in multi-tenant deployments — wire the tenant-aware adapter
 * instead.</p>
 */
@Service
public class KbEnvCredentialLookup implements KbCredentialLookupPort {

    private static final Logger log = LoggerFactory.getLogger(KbEnvCredentialLookup.class);

    @Override
    public ResolvedKbCredential resolve(UUID tenantId, String embeddingModel) {
        if (tenantId != null) {
            log.warn("kb credential env-fallback in use tenantId={} (no tenant-scoped resolver available)", tenantId);
        }
        String model = embeddingModel == null ? "" : embeddingModel.toLowerCase();
        if (KbEmbeddingModelFactory.isOllamaModel(model)) {
            String baseUrl = System.getenv("OLLAMA_BASE_URL");
            return new ResolvedKbCredential(null,
                    baseUrl == null || baseUrl.isBlank() ? KbEmbeddingModelFactory.OLLAMA_DEFAULT_BASE_URL : baseUrl);
        }
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY not configured for knowledge embeddings");
        }
        return new ResolvedKbCredential(apiKey, null);
    }
}
