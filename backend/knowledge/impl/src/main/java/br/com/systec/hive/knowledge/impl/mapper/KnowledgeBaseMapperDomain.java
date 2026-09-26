package br.com.systec.hive.knowledge.impl.mapper;

import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = org.mapstruct.InjectionStrategy.CONSTRUCTOR)
public interface KnowledgeBaseMapperDomain {

    KnowledgeBase toDomain(KnowledgeBaseEntity entity);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    KnowledgeBaseEntity toEntity(KnowledgeBase domain);

    KbDocument toDomain(KbDocumentEntity entity);

    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    KbDocumentEntity toEntity(KbDocument domain);
}
