package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ProviderCredential;
import br.com.systec.hive.agent.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.agent.impl.mapper.ProviderCredentialMapperDomain;
import br.com.systec.hive.agent.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderCredentialServiceImplTest {

    @Mock
    private ProviderCredentialRepository repository;

    @Spy
    private ProviderCredentialMapperDomain mapper = Mappers.getMapper(ProviderCredentialMapperDomain.class);

    @InjectMocks
    private ProviderCredentialServiceImpl service;

    @Test
    void when_upsert_with_validKey_then_returnsMaskedStatus() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENAI)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderCredential result = service.upsert(tenantId, AiProvider.OPENAI, "sk-test-fake-1234", null);

        assertThat(result.isConfigured()).isTrue();
        assertThat(result.getLast4()).isEqualTo("1234");
        assertThat(result.getProvider()).isEqualTo(AiProvider.OPENAI);
        ArgumentCaptor<ProviderCredentialEntity> captor = ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void when_upsert_with_wrongPrefix_then_throwsAndNeverSaves() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(tenantId, AiProvider.OPENAI, "AIzaWrong", null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void when_upsert_with_newCredential_then_keyVersionOne() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENCODE_GO)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(tenantId, AiProvider.OPENCODE_GO, "go-fake-key", null);

        ArgumentCaptor<ProviderCredentialEntity> captor = ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKeyVersion()).isEqualTo(1);
    }

    @Test
    void when_upsert_with_rotatedKey_then_keyVersionIncrements() {
        UUID tenantId = UUID.randomUUID();
        ProviderCredentialEntity existing = new ProviderCredentialEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setProvider(AiProvider.OPENAI);
        existing.setApiKey("sk-old-key-1111");
        existing.setKeyVersion(2);
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENAI))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(tenantId, AiProvider.OPENAI, "sk-new-key-2222", null);

        ArgumentCaptor<ProviderCredentialEntity> captor = ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKeyVersion()).isEqualTo(3);
    }

    @Test
    void when_upsert_with_sameKey_then_keyVersionPreserved() {
        UUID tenantId = UUID.randomUUID();
        ProviderCredentialEntity existing = new ProviderCredentialEntity();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setProvider(AiProvider.OPENAI);
        existing.setApiKey("sk-same-key-1234");
        existing.setKeyVersion(2);
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENAI))
                .thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(tenantId, AiProvider.OPENAI, "sk-same-key-1234", null);

        ArgumentCaptor<ProviderCredentialEntity> captor = ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getKeyVersion()).isEqualTo(2);
    }

    @Test
    void when_upsert_with_goZenEchoOfFixedVendorUrl_then_acceptedButNotPersisted() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.OPENCODE_GO)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProviderCredential result = service.upsert(tenantId, AiProvider.OPENCODE_GO, "go-fake-key",
                "https://opencode.ai/zen/go/v1");

        assertThat(result.isConfigured()).isTrue();
        ArgumentCaptor<ProviderCredentialEntity> captor = ArgumentCaptor.forClass(ProviderCredentialEntity.class);
        verify(repository).save(captor.capture());
        // baseUrl fixa é constante do vendor, nunca estado do tenant
        assertThat(captor.getValue().getBaseUrl()).isNull();
        assertThat(result.getBaseUrl()).isNull();
    }

    @Test
    void when_upsert_with_goZenOverrideAttempt_then_throwsAndNeverSaves() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(tenantId, AiProvider.OPENCODE_GO, "go-fake-key",
                "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");

        verify(repository, never()).save(any());
    }

    @Test
    void when_delete_with_existing_then_deletes() {        UUID tenantId = UUID.randomUUID();
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setTenantId(tenantId);
        entity.setProvider(AiProvider.GEMINI);
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.GEMINI))
                .thenReturn(Optional.of(entity));

        service.delete(tenantId, AiProvider.GEMINI);

        verify(repository).delete(entity);
    }

    @Test
    void when_delete_with_missing_then_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantIdAndProvider(tenantId, AiProvider.GEMINI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, AiProvider.GEMINI))
                .isInstanceOf(ObjectNotFoundException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void when_listStatuses_with_oneStored_then_returnsAllVendors() {
        UUID tenantId = UUID.randomUUID();
        ProviderCredentialEntity entity = new ProviderCredentialEntity();
        entity.setTenantId(tenantId);
        entity.setProvider(AiProvider.OPENAI);
        entity.setApiKey("sk-test-fake-1234");
        when(repository.findByTenantId(tenantId)).thenReturn(List.of(entity));

        List<ProviderCredential> result = service.listStatuses(tenantId);

        assertThat(result).hasSize(AiProvider.values().length);
        ProviderCredential openai = result.stream()
                .filter(c -> c.getProvider() == AiProvider.OPENAI).findFirst().orElseThrow();
        assertThat(openai.isConfigured()).isTrue();
        assertThat(openai.getLast4()).isEqualTo("1234");
        assertThat(result.stream().filter(c -> c.getProvider() != AiProvider.OPENAI))
                .allMatch(c -> !c.isConfigured() && c.getLast4() == null);
    }
}
