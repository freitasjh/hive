package br.com.systec.hive.assistant.impl.mapper;

import br.com.systec.hive.assistant.api.domain.ProviderCredential;
import br.com.systec.hive.assistant.impl.entity.ProviderCredentialEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface ProviderCredentialMapperDomain {

    @Mapping(target = "configured", ignore = true)
    @Mapping(target = "last4", ignore = true)
    ProviderCredential toDomain(ProviderCredentialEntity entity);
}
