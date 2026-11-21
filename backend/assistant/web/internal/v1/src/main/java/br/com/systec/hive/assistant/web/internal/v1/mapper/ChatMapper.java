package br.com.systec.hive.assistant.web.internal.v1.mapper;

import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatRequestDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    ChatRequest toDomain(ChatRequestDTO dto);

    @Mapping(target = "role", source = "role", qualifiedByName = "roleToString")
    ChatResponseDTO toResponse(ChatResponse response);

    @Named("roleToString")
    default String roleToString(Object role) {
        if (role == null) return "assistant";
        return role.toString().toLowerCase();
    }
}
