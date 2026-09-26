package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.ProviderCredential;
import br.com.systec.hive.agent.impl.entity.ProviderCredentialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ProviderCredentialMapperDomain {

    @Mapping(target = "configured", ignore = true)
    @Mapping(target = "last4", ignore = true)
    ProviderCredential toDomain(ProviderCredentialEntity entity);
}
