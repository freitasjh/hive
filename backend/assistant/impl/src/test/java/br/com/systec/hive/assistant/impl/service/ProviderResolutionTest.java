package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.exceptions.TenantLlmConfigNotFoundException;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import br.com.systec.hive.assistant.api.exceptions.ConfigMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three reads behind one provider decision, and the order they happen in.
 *
 * <p>The order is the behaviour under test, not the plumbing: a tenant with no configuration is told
 * to configure one, a model the vendor does not carry is refused before any credential is looked up,
 * and a missing credential is the last thing checked because it is the only one the tenant can fix
 * without touching the model.
 */
@ExtendWith(MockitoExtension.class)
class ProviderResolutionTest {

    @Mock
    private TenantLlmConfigService tenantLlmConfigService;

    @Mock
    private CredentialResolver credentialResolver;

    @InjectMocks
    private ProviderResolution resolution;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void when_tenantHasNoLlmConfig_then_throws422AndAsksForNoCredential() {
        when(tenantLlmConfigService.require(tenantId)).thenThrow(new TenantLlmConfigNotFoundException());

        assertThatThrownBy(() -> resolution.resolve(tenantId))
                .isInstanceOf(TenantLlmConfigNotFoundException.class);

        verify(credentialResolver, never()).resolve(any(), any());
    }

    @Test
    void when_modelIsOutsideTheVendorCatalog_then_throws400AndResolvesNoCredential() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(AiProvider.OPENAI);
        config.setModel("llama3.2");
        when(tenantLlmConfigService.require(tenantId)).thenReturn(config);

        assertThatThrownBy(() -> resolution.resolve(tenantId))
                .isInstanceOf(ModelNotSupportedForProviderException.class);

        verify(credentialResolver, never()).resolve(any(), any());
    }

    @Test
    void when_credentialIsAbsent_then_throws422NamingTheProvider() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(AiProvider.OPENAI);
        config.setModel("gpt-4o-mini");
        when(tenantLlmConfigService.require(tenantId)).thenReturn(config);
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenThrow(new ConfigMissingException("OPENAI"));

        assertThatThrownBy(() -> resolution.resolve(tenantId))
                .isInstanceOf(ConfigMissingException.class)
                .hasMessageContaining("OPENAI");
    }

    @Test
    void when_everythingIsConfigured_then_handsTheTurnProviderModelAndCredential() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(AiProvider.OLLAMA);
        config.setModel("llama3.2");
        when(tenantLlmConfigService.require(tenantId)).thenReturn(config);
        when(credentialResolver.resolve(tenantId, AiProvider.OLLAMA))
                .thenReturn(new CredentialResolver.ResolvedCredential(null, "http://localhost:11434"));

        ProviderResolution.Resolved resolved = resolution.resolve(tenantId);

        assertThat(resolved.provider()).isEqualTo(AiProvider.OLLAMA);
        assertThat(resolved.model()).isEqualTo("llama3.2");
        assertThat(resolved.apiKey()).isNull();
        assertThat(resolved.baseUrl()).isEqualTo("http://localhost:11434");
    }
}
