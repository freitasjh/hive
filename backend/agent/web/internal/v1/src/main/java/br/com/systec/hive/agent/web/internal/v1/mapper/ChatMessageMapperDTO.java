package br.com.systec.hive.agent.web.internal.v1.mapper;

import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatMessageDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Mapper(componentModel = "spring")
public interface ChatMessageMapperDTO {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "sessionId", source = "sessionId")
    @Mapping(target = "role", expression = "java(mapRole(domain.getRole()))")
    @Mapping(target = "content", expression = "java(domain.getContent() != null ? domain.getContent() : domain.getMessage())")
    @Mapping(target = "createAt", expression = "java(toInstant(domain.getCreateAt()))")
    ChatMessageDTO toDto(ChatMessage domain);

    default String mapRole(Object role) {
        if (role == null) return null;
        String name = role.toString();
        if (name == null) return null;
        return name.toLowerCase().contains("assistant") ? "assistant" : "user";
    }

    default Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
