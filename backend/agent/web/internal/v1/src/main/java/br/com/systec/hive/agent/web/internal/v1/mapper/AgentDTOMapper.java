package br.com.systec.hive.agent.web.internal.v1.mapper;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentCreateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentUpdateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface AgentDTOMapper {

    @Mapping(target = "promptTemplateId", expression = "java(agent.getPromptTemplate() != null ? agent.getPromptTemplate().getId() : null)")
    @Mapping(target = "createAt", expression = "java(toInstant(agent.getCreateAt()))")
    @Mapping(target = "updateAt", expression = "java(toInstant(agent.getUpdateAt()))")
    AgentDTO toDto(Agent agent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "promptTemplate", expression = "java(toPromptTemplate(dto.promptTemplateId()))")
    @Mapping(target = "isDefault", expression = "java(dto.isDefault() != null ? dto.isDefault() : false)")
    @Mapping(target = "active", expression = "java(dto.active() != null ? dto.active() : true)")
    Agent toDomain(AgentCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "promptTemplate", expression = "java(toPromptTemplate(dto.promptTemplateId()))")
    @Mapping(target = "isDefault", source = "isDefault")
    @Mapping(target = "active", source = "active")
    Agent toDomain(AgentUpdateDTO dto);

    default PromptTemplate toPromptTemplate(UUID id) {
        if (id == null) return null;
        PromptTemplate pt = new PromptTemplate();
        pt.setId(id);
        return pt;
    }

    default Instant toInstant(LocalDateTime ldt) {
        if (ldt == null) return null;
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
