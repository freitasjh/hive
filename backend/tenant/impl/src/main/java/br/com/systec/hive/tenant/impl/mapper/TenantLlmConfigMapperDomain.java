package br.com.systec.hive.tenant.impl.mapper;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.tenant.impl.entity.TenantLlmConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code tenantId} is ignored on the way in on purpose: it is the row's identity, assigned by the
 * caller that owns the tenant context, and a payload that could set it would let one tenant's upsert
 * land on another's row.
 */
@Mapper(componentModel = "spring")
public interface TenantLlmConfigMapperDomain {

    TenantLlmConfig toDomain(TenantLlmConfigEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    TenantLlmConfigEntity toEntity(TenantLlmConfig domain);
}
