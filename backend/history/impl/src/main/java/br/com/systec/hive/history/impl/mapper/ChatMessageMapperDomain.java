package br.com.systec.hive.history.impl.mapper;

import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.history.impl.entity.ChatMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatMessageMapperDomain {

    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "createAt", source = "createAt")
    ChatMessage toDomain(ChatMessageEntity entity);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "id", ignore = true)
    ChatMessageEntity toEntity(ChatMessage domain);
}
