package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.exceptions.ConfigMissingException;
import br.com.systec.hive.agent.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.agent.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.provider.api.AiProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class CredentialResolver {

    public record ResolvedCredential(String apiKey, String baseUrl) {
    }

    public static final String OLLAMA_DEFAULT_BASE_URL = "http://localhost:11434";

    private final ProviderCredentialRepository repository;
    private final Function<String, String> envReader;

    @Autowired
    public CredentialResolver(ProviderCredentialRepository repository) {
        this(repository, System::getenv);
    }

    CredentialResolver(ProviderCredentialRepository repository, Function<String, String> envReader) {
        this.repository = repository;
        this.envReader = envReader;
    }

    public ResolvedCredential resolve(UUID tenantId, AiProvider provider) {
        Optional<ProviderCredentialEntity> stored = repository.findByTenantIdAndProvider(tenantId, provider);
        if (stored.isPresent()) {
            ProviderCredentialEntity entity = stored.get();
            String baseUrl = entity.getBaseUrl();
            if (baseUrl == null && provider == AiProvider.OLLAMA) {
                baseUrl = OLLAMA_DEFAULT_BASE_URL;
            }
            return new ResolvedCredential(entity.getApiKey(), baseUrl);
        }
        if (provider == AiProvider.OLLAMA) {
            String baseUrl = envReader.apply("OLLAMA_BASE_URL");
            return new ResolvedCredential(null,
                    baseUrl == null || baseUrl.isBlank() ? OLLAMA_DEFAULT_BASE_URL : baseUrl);
        }
        String apiKey = envReader.apply(keyEnvName(provider));
        if (apiKey == null || apiKey.isBlank()) {
            throw new ConfigMissingException(String.valueOf(provider));
        }
        String baseUrl = null;
        String baseUrlEnv = baseUrlEnvName(provider);
        if (baseUrlEnv != null) {
            baseUrl = envReader.apply(baseUrlEnv);
        }
        return new ResolvedCredential(apiKey, baseUrl);
    }

    static String keyEnvName(AiProvider provider) {
        return switch (provider) {
            case OPENAI -> "OPENAI_API_KEY";
            case GEMINI -> "GEMINI_API_KEY";
            case ANTHROPIC -> "ANTHROPIC_API_KEY";
            case OPENAI_COMPATIBLE -> "OPENAI_COMPATIBLE_API_KEY";
            case OPENCODE_GO -> "OPENCODE_GO_API_KEY";
            case OPENCODE_ZEN -> "OPENCODE_ZEN_API_KEY";
            case OLLAMA -> null;
        };
    }

    static String baseUrlEnvName(AiProvider provider) {
        return switch (provider) {
            case OLLAMA -> "OLLAMA_BASE_URL";
            case OPENAI_COMPATIBLE -> "OPENAI_COMPATIBLE_BASE_URL";
            case OPENCODE_GO -> null;
            case OPENCODE_ZEN -> null;
            default -> null;
        };
    }
}
