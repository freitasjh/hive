package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.impl.data.PromptTemplateDataFactory;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PromptTemplateDomainMapperTest {

    private final PromptTemplateDomainMapper mapper = Mappers.getMapper(PromptTemplateDomainMapper.class);

    @Test
    void toEntity() {
        PromptTemplate domain = PromptTemplateDataFactory.createPromptTemplate();

        PromptTemplateEntity entity = mapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals(domain.getId(), entity.getId());
        assertEquals(domain.getName(), entity.getName());
        assertEquals(domain.getTemplate(), entity.getTemplate());
    }

    @Test
    void toDomain() {
        PromptTemplateEntity entity = PromptTemplateDataFactory.createPromptTemplateEntity();

        PromptTemplate domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(entity.getId(), domain.getId());
        assertEquals(entity.getName(), domain.getName());
        assertEquals(entity.getTemplate(), domain.getTemplate());
    }

    @Test
    void updateEntity() {
        PromptTemplate domain = PromptTemplateDataFactory.createPromptTemplate();
        domain.setName("Updated Name");
        domain.setTemplate("Updated Template");

        PromptTemplateEntity entity = PromptTemplateDataFactory.createPromptTemplateEntity();
        mapper.updateEntity(entity, domain);

        assertEquals(domain.getName(), entity.getName());
        assertEquals(domain.getTemplate(), entity.getTemplate());
    }

    @Test
    void toDomainList() {
        List<PromptTemplateEntity> entityList = Collections.singletonList(PromptTemplateDataFactory.createPromptTemplateEntity());

        List<PromptTemplate> domainList = mapper.toDomain(entityList);

        assertNotNull(domainList);
        assertEquals(1, domainList.size());
        assertEquals(entityList.getFirst().getId(), domainList.getFirst().getId());
        assertEquals(entityList.getFirst().getName(), domainList.getFirst().getName());
        assertEquals(entityList.getFirst().getTemplate(), domainList.getFirst().getTemplate());
    }

}
