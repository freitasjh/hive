package br.com.systec.hive.agent.web.internal.v1.mapper;

import br.com.systec.hive.agent.api.domain.ChatRequest;
import br.com.systec.hive.agent.api.domain.ChatResponse;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatRequestDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(target = "agent.id", source = "agentId")
    ChatRequest toDomain(ChatRequestDTO dto);

    @Mapping(target = "role", source = "role", qualifiedByName = "roleToString")
    @Mapping(target = "citations", source = "citations", qualifiedByName = "citationsToDto")
    ChatResponseDTO toResponse(ChatResponse response);

    @Named("roleToString")
    default String roleToString(Object role) {
        if (role == null) return "assistant";
        return role.toString().toLowerCase();
    }

    @Named("citationsToDto")
    default java.util.List<br.com.systec.hive.agent.web.internal.v1.dto.ChatResponseDTO.CitationDTO> citationsToDto(
            java.util.List<br.com.systec.hive.knowledge.api.domain.KbCitation> citations) {
        if (citations == null) return java.util.List.of();
        return citations.stream()
                .map(c -> new br.com.systec.hive.agent.web.internal.v1.dto.ChatResponseDTO.CitationDTO(
                        c.getDocId(), c.getDocName(), c.getChunkId(), c.getScore()))
                .toList();
    }
}
