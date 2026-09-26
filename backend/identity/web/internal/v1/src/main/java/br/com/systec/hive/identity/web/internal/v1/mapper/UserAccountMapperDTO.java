package br.com.systec.hive.identity.web.internal.v1.mapper;

import br.com.systec.hive.identity.api.domain.UserAccount;
import br.com.systec.hive.identity.web.internal.v1.dto.UserAccountCreateDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAccountMapperDTO {

    UserAccount toDomain(UserAccountCreateDTO createDTO);
}
