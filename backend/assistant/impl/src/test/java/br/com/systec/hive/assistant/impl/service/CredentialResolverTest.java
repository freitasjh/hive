package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.exceptions.ConfigMissingException;
import br.com.systec.hive.assistant.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.assistant.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialResolverTest {

    @Mock
    private ProviderCredentialRepository repository;

    private final Map<String, String> env = new HashMap<>();

    private CredentialResolver resolver() {
        return new CredentialResolver(repository, env::get);
    }

    private ProviderCredentialEntity entity(UUID tenantId, AiProvider provider, String apiKey, String baseUrl) {
        ProviderCredentialEntity e = new ProviderCredentialEntity();
        e.setTenantId(tenantId);
        e.setProvider(provider);
        e.setApiKey(apiKey);
        e.setBaseUrl(baseUrl);
        e.setKeyVersion(1);
        return e;
    }

    @Test
    void when_resolve_with_storedCredential_then_returnsStoredValues() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENAI))
                .thenReturn(Optional.of(entity(tenantId, AiProvider.OPENAI, "sk-stored-fake", null)));

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OPENAI);

        assertThat(resolved.apiKey()).isEqualTo("sk-stored-fake");
    }

    @Test
    void when_resolve_withoutStored_butEnvPresent_then_returnsEnvFallback() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());
        env.put("OPENAI_API_KEY", "sk-env-fake");

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OPENAI);

        assertThat(resolved.apiKey()).isEqualTo("sk-env-fake");
    }

    @Test
    void when_resolve_withoutStoredNorEnv_then_throwsConfigMissing() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve(tenantId, AiProvider.ANTHROPIC))
                .isInstanceOf(ConfigMissingException.class);
    }

    @Test
    void when_resolve_with_ollamaWithoutKey_then_resolvesWithoutKey() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OLLAMA);

        assertThat(resolved.apiKey()).isNull();
        assertThat(resolved.baseUrl()).isEqualTo("http://localhost:11434");
    }

    @Test
    void when_resolve_with_storedCredential_then_storedWinsOverEnv() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.GEMINI))
                .thenReturn(Optional.of(entity(tenantId, AiProvider.GEMINI, "AIzaStoredFake", null)));
        env.put("GEMINI_API_KEY", "AIzaEnvFake");

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.GEMINI);

        assertThat(resolved.apiKey()).isEqualTo("AIzaStoredFake");
    }

    @Test
    void when_resolve_with_goWithoutStored_butEnvPresent_then_returnsEnvFallback() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());
        env.put("OPENCODE_GO_API_KEY", "go-env-fake");

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OPENCODE_GO);

        assertThat(resolved.apiKey()).isEqualTo("go-env-fake");
        assertThat(resolved.baseUrl()).isNull();
    }

    @Test
    void when_resolve_with_zenWithoutStored_butEnvPresent_then_returnsEnvFallback() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());
        env.put("OPENCODE_ZEN_API_KEY", "zen-env-fake");

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OPENCODE_ZEN);

        assertThat(resolved.apiKey()).isEqualTo("zen-env-fake");
        assertThat(resolved.baseUrl()).isNull();
    }

    @Test
    void when_resolve_with_goStored_then_storedWinsOverEnvAndBaseUrlNull() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(Optional.of(entity(tenantId, AiProvider.OPENCODE_GO, "go-stored-fake", null)));
        env.put("OPENCODE_GO_API_KEY", "go-env-fake");

        CredentialResolver.ResolvedCredential resolved = resolver().resolve(tenantId, AiProvider.OPENCODE_GO);

        assertThat(resolved.apiKey()).isEqualTo("go-stored-fake");
        assertThat(resolved.baseUrl()).isNull();
    }

    @Test
    void when_resolve_with_goWithoutStoredNorEnv_then_throwsConfigMissing() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve(tenantId, AiProvider.OPENCODE_GO))
                .isInstanceOf(ConfigMissingException.class);
        assertThatThrownBy(() -> resolver().resolve(tenantId, AiProvider.OPENCODE_ZEN))
                .isInstanceOf(ConfigMissingException.class);
    }
}
