package br.com.systec.hive.tenant.impl.mapper;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.tenant.impl.entity.TenantLlmConfigEntity;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MapStruct is exercised through {@code Mappers.getMapper}, never mocked: a mock would assert that
 * the mapper was called, which is not what a mapper is for.
 */
class TenantLlmConfigMapperDomainTest {

    private final TenantLlmConfigMapperDomain mapper = Mappers.getMapper(TenantLlmConfigMapperDomain.class);

    @Test
    void when_toDomain_then_carriesProviderAndModel() {
        TenantLlmConfigEntity entity = new TenantLlmConfigEntity();
        entity.setProvider(AiProvider.OPENCODE_GO);
        entity.setModel("mimo-v2.5");

        TenantLlmConfig domain = mapper.toDomain(entity);

        assertThat(domain.getProvider()).isEqualTo(AiProvider.OPENCODE_GO);
        assertThat(domain.getModel()).isEqualTo("mimo-v2.5");
    }

    @Test
    void when_toEntity_then_ignoresTenantIdSoAPayloadCannotAddressAnotherTenant() {
        // tenantId é a identidade da linha, atribuída por quem tem o contexto do tenant. A entidade
        // tem o campo (AbstractEntity) e o mapper o ignora de propósito: se o copiasse, um upsert
        // poderia aterrissar na configuração de outro tenant. O VO, aliás, nem tem setTenantId —
        // não há como o dado chegar vindo do cliente.
        TenantLlmConfig domain = new TenantLlmConfig();
        domain.setProvider(AiProvider.OPENAI);
        domain.setModel("gpt-4o-mini");
        domain.setId(UUID.randomUUID());

        TenantLlmConfigEntity entity = mapper.toEntity(domain);

        assertThat(entity.getProvider()).isEqualTo(AiProvider.OPENAI);
        assertThat(entity.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(entity.getTenantId()).isNull();
        assertThat(entity.getId()).isNull();
    }
}
