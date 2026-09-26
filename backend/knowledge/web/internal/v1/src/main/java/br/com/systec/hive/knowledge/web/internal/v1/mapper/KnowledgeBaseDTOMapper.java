package br.com.systec.hive.knowledge.web.internal.v1.mapper;

import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KbDocumentDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseCreateDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseDTO;
import br.com.systec.hive.knowledge.web.internal.v1.dto.KnowledgeBaseUpdateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface KnowledgeBaseDTOMapper {

    KnowledgeBase toDomain(KnowledgeBaseCreateDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "embeddingModel", ignore = true)
    @Mapping(target = "dimension", ignore = true)
    KnowledgeBase toDomain(KnowledgeBaseUpdateDTO dto);

    KnowledgeBaseDTO toDto(KnowledgeBase domain);

    KbDocumentDTO toDto(KbDocument domain);
}
