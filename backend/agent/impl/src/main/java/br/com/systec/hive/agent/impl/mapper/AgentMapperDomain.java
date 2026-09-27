package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {PromptTemplateDomainMapper.class}, injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface AgentMapperDomain {

    @Mapping(target = "prompt", expression = "java(agentEntity.getPromptTemplate() != null ? agentEntity.getPromptTemplate().getTemplate() : null)")
    @Mapping(target = "promptTemplate", source = "promptTemplate")
    @Mapping(target = "systemPrompt", source = "systemPrompt")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "topP", source = "topP")
    @Mapping(target = "maxTokens", source = "maxTokens")
    @Mapping(target = "tools", source = "tools")
    /**
     * Ignored, not merely absent: MapStruct maps same-named fields implicitly, so dropping the line
     * would keep the mapping. The column outlived the link and still carries names of bases that no
     * longer exist, and reading them out is how the API started answering with dangling values.
     */
    @Mapping(target = "knowledgeBases", ignore = true)
    @Mapping(target = "isDefault", source = "isDefault")
    Agent toDomain(AgentEntity agentEntity);

    @Mapping(target = "promptTemplate", source = "promptTemplate")
    @Mapping(target = "systemPrompt", source = "systemPrompt")
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    @Mapping(target = "topP", source = "topP")
    @Mapping(target = "maxTokens", source = "maxTokens")
    @Mapping(target = "tools", source = "tools")
    @Mapping(target = "knowledgeBases", source = "knowledgeBases")
    @Mapping(target = "isDefault", source = "isDefault")
    AgentEntity toEntity(Agent agent);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "isDefault", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(@MappingTarget AgentEntity agentEntity, Agent agent);
}
