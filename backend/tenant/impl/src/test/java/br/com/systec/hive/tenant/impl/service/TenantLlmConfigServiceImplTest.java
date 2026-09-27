package br.com.systec.hive.tenant.impl.service;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.exceptions.ConcurrentLlmConfigUpdateException;
import br.com.systec.hive.api.exceptions.ProviderNotAllowedException;
import br.com.systec.hive.api.exceptions.TenantLlmConfigNotFoundException;
import br.com.systec.hive.tenant.impl.entity.TenantLlmConfigEntity;
import br.com.systec.hive.tenant.impl.mapper.TenantLlmConfigMapperDomain;
import br.com.systec.hive.tenant.impl.repository.TenantLlmConfigRepository;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantLlmConfigServiceImplTest {

    @Mock
    private TenantLlmConfigRepository repository;

    @Mock
    private TenantLlmConfigMapperDomain mapper;

    @InjectMocks
    private TenantLlmConfigServiceImpl service;

    // ============================================================ require

    @Test
    void when_require_withConfiguration_then_returnsTheDomain() {
        UUID tenantId = UUID.randomUUID();
        TenantLlmConfigEntity entity = new TenantLlmConfigEntity();
        entity.setProvider(AiProvider.OPENAI);
        entity.setModel("gpt-4o-mini");
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(entity));
        TenantLlmConfig expected = new TenantLlmConfig();
        when(mapper.toDomain(entity)).thenReturn(expected);

        assertThat(service.require(tenantId)).isSameAs(expected);
    }

    @Test
    void when_require_withoutConfiguration_then_422TellingWhatToConfigure() {
        // O comportamento pedido: sem config o chat falha com erro claro e acionável. Não há
        // fallback global e não há criação automática — um default implícito esconderia a
        // configuração ausente atrás de algo que "funciona" e não é diagnosticável.
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.require(tenantId))
                .isInstanceOf(TenantLlmConfigNotFoundException.class)
                .hasMessageContaining("Configurações > Provedores");
    }

    @Test
    void when_get_withoutConfiguration_then_nullRatherThanAnException() {
        // A tela de configuração precisa distinguir "não configurado" de "erro", então a leitura é
        // Nullable e só o caminho do chat exige.
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThat(service.get(tenantId)).isNull();
    }

    // ============================================================ upsert

    @Test
    void when_upsert_withoutExistingRow_then_createsItOwnedByTheTenant() {
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any(TenantLlmConfigEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toDomain(any(TenantLlmConfigEntity.class))).thenReturn(new TenantLlmConfig());

        service.upsert(tenantId, config(AiProvider.OPENAI, "gpt-4o-mini"));

        ArgumentCaptor<TenantLlmConfigEntity> captor = ArgumentCaptor.forClass(TenantLlmConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(captor.getValue().getProvider()).isEqualTo(AiProvider.OPENAI);
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void when_upsert_withExistingRow_then_updatesInPlaceInsteadOfInsertingASecondOne() {
        UUID tenantId = UUID.randomUUID();
        TenantLlmConfigEntity existing = new TenantLlmConfigEntity();
        existing.setTenantId(tenantId);
        existing.setProvider(AiProvider.OPENAI);
        existing.setModel("gpt-4o-mini");
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(repository.save(any(TenantLlmConfigEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toDomain(any(TenantLlmConfigEntity.class))).thenReturn(new TenantLlmConfig());

        service.upsert(tenantId, config(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5"));

        verify(repository).save(existing);
        assertThat(existing.getProvider()).isEqualTo(AiProvider.OPENCODE_ZEN);
        assertThat(existing.getModel()).isEqualTo("claude-sonnet-4-5");
    }

    @Test
    void when_upsert_withPaddedModel_then_trimsIt() {
        // Um " gpt-4o-mini " persistido volta como literal na tela e no trace, e não casa com o
        // catálogo. O banco só rejeita string toda em branco; o espaço nas pontas é nosso.
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any(TenantLlmConfigEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toDomain(any(TenantLlmConfigEntity.class))).thenReturn(new TenantLlmConfig());

        service.upsert(tenantId, config(AiProvider.OPENAI, "  gpt-4o-mini  "));

        ArgumentCaptor<TenantLlmConfigEntity> captor = ArgumentCaptor.forClass(TenantLlmConfigEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void when_upsert_withNullProvider_then_422AndNothingIsWritten() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(tenantId, config(null, "gpt-4o-mini")))
                .isInstanceOf(ProviderNotAllowedException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void when_upsert_withBlankModel_then_rejectedAndNothingIsWritten() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> service.upsert(tenantId, config(AiProvider.OPENAI, "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");

        verify(repository, never()).save(any());
    }

    @Test
    void when_upsert_withNullConfig_then_rejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> service.upsert(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void when_upsert_hitsTheUniqueKey_then_409AndNotADataIntegrityError() {
        // Duas requisições simultâneas leem "ausente" e as duas inserem. Deixar a UNIQUE falar é
        // melhor que um lock de read-modify-write que alguém precisaria lembrar de tomar; e a
        // exceção de domínio não pode vazar o nome da constraint (OWASP A05).
        UUID tenantId = UUID.randomUUID();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(repository.save(any(TenantLlmConfigEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_tenant_llm_config_tenant\""));

        assertThatThrownBy(() -> service.upsert(tenantId, config(AiProvider.OPENAI, "gpt-4o-mini")))
                .isInstanceOf(ConcurrentLlmConfigUpdateException.class)
                .hasMessageNotContaining("uk_tenant_llm_config_tenant")
                .hasMessageContaining("Tente de novo");
    }

    // ============================================================ isolamento

    @Test
    void when_reading_then_theRepositoryIsAlwaysGivenTheTenant() {
        // O filtro Hibernate de tenant é defesa em profundidade, não o contrato: uma query sem
        // tenantId leria a linha de todos os tenants. Um repositório cujos finders exigem o
        // tenantId não pode ser chamado assim.
        UUID tenantId = UUID.randomUUID();
        TenantLlmConfigEntity entity = new TenantLlmConfigEntity();
        when(repository.findByTenantId(tenantId)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(new TenantLlmConfig());

        service.get(tenantId);
        service.require(tenantId);

        verify(repository, org.mockito.Mockito.times(2)).findByTenantId(tenantId);
        verify(repository, never()).findAll();
    }

    private static TenantLlmConfig config(AiProvider provider, String model) {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(provider);
        config.setModel(model);
        return config;
    }
}
