package br.com.systec.hive.assistant.impl.mapper;

import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.impl.entity.PromptTemplateEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PromptTemplateDomainMapper {

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    PromptTemplateEntity toEntity(PromptTemplate promptTemplate);

    PromptTemplate toDomain(PromptTemplateEntity promptTemplateEntity);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    void updateEntity(@MappingTarget PromptTemplateEntity promptTemplateEntity, PromptTemplate promptTemplate);

    List<PromptTemplate> toDomain(List<PromptTemplateEntity> promptTemplateEntities);

}
