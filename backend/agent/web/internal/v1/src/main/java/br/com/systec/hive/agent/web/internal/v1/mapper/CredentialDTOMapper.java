package br.com.systec.hive.agent.web.internal.v1.mapper;

import br.com.systec.hive.agent.api.domain.ProviderCredential;
import br.com.systec.hive.agent.web.internal.v1.dto.CredentialStatusDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface CredentialDTOMapper {

    CredentialStatusDTO toDto(ProviderCredential domain);
}
