package br.com.systec.hive.identity.impl.mapper;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.impl.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mappings({
            @Mapping(target = "authorities", ignore = true),
            @Mapping(target = "updateAt", ignore = true)
    })
    UserEntity toEntity(User user);

    @Mappings({
            @Mapping(target = "authorities", ignore = true),
    })
    User toDomain(UserEntity userEntity);
}
