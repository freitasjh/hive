package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.knowledge.api.port.KbCredentialLookupPort;
import br.com.systec.hive.provider.api.AiProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Primary
public class KbTenantCredentialAdapter implements KbCredentialLookupPort {

    private final CredentialResolver credentialResolver;

    public KbTenantCredentialAdapter(CredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @Override
    public ResolvedKbCredential resolve(UUID tenantId, String embeddingModel) {
        AiProvider provider = providerFor(embeddingModel);
        if (tenantId == null) {
            return resolveEnvFallback(provider);
        }
        CredentialResolver.ResolvedCredential resolved = credentialResolver.resolve(tenantId, provider);
        return new ResolvedKbCredential(resolved.apiKey(), resolved.baseUrl());
    }

    static AiProvider providerFor(String embeddingModel) {
        if (embeddingModel == null) {
            return AiProvider.OPENAI;
        }
        String m = embeddingModel.toLowerCase();
        if (m.contains("nomic") || m.contains("minilm") || m.contains("mxbai") || m.contains("llama")) {
            return AiProvider.OLLAMA;
        }
        if (m.contains("gemini") || m.contains("text-embedding-004")) {
            return AiProvider.GEMINI;
        }
        return AiProvider.OPENAI;
    }

    private ResolvedKbCredential resolveEnvFallback(AiProvider provider) {
        if (provider == AiProvider.OLLAMA) {
            String baseUrl = System.getenv("OLLAMA_BASE_URL");
            return new ResolvedKbCredential(null,
                    baseUrl == null || baseUrl.isBlank() ? CredentialResolver.OLLAMA_DEFAULT_BASE_URL : baseUrl);
        }
        return new ResolvedKbCredential(System.getenv("OPENAI_API_KEY"), null);
    }
}
