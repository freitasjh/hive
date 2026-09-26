package br.com.systec.hive.tenant.impl.mapper;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.tenant.impl.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TenantMapper {

    TenantMapper INSTANCE = Mappers.getMapper(TenantMapper.class);

    TenantEntity toEntity(Tenant tenant);

    Tenant toDomain(TenantEntity tenantEntity);
}
