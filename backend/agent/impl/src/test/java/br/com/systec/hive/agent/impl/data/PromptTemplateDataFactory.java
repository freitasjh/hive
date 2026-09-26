package br.com.systec.hive.agent.impl.data;

import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;

import java.util.UUID;

public class PromptTemplateDataFactory {

    public static PromptTemplateEntity createPromptTemplateEntity() {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Test Template");
        entity.setTemplate("This is a test template.");
        return entity;
    }

    public static PromptTemplate createPromptTemplate() {
        PromptTemplate domain = new PromptTemplate();
        domain.setId(UUID.randomUUID());
        domain.setName("Test Template");
        domain.setTemplate("This is a test template.");
        return domain;
    }
}
