package br.com.systec.hive.security.web.internal.v1.mapper;

import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.web.internal.v1.dto.LoginDTO;
import br.com.systec.hive.security.web.internal.v1.dto.LoginResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoginMapperDTO {

    Login toDomain(LoginDTO loginDTO);

    @Mapping(target = "type", constant = "Bearer")
    LoginResponseDTO toLoginResponse(LoginAuthenticate loginAuthenticate);
}
