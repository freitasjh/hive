package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatSessionMapperDomain {

    ChatSession toDomain(ChatSessionEntity entity);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    ChatSessionEntity toEntity(ChatSession domain);
}
