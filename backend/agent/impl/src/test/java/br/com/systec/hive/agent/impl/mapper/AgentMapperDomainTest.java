package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.impl.data.AgentDataFactory;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AgentMapperDomainTest {

    private final AgentMapperDomain mapper = new AgentMapperDomainImpl(new PromptTemplateDomainMapperImpl());

    @Test
    void toDomain() {
        AgentEntity agentEntity = AgentDataFactory.createAgentEntity();
        PromptTemplateEntity templateEntity = new PromptTemplateEntity();
        templateEntity.setId(UUID.randomUUID());
        templateEntity.setCode("CODE");
        templateEntity.setName("Template");
        templateEntity.setTemplate("Hello {{name}}");
        templateEntity.setVersion(1);
        templateEntity.setStatus(PromptStatus.DRAFT);
        templateEntity.setParams(Set.of("name"));
        agentEntity.setPromptTemplate(templateEntity);

        Agent agent = mapper.toDomain(agentEntity);

        assertNotNull(agent);
        assertEquals(agentEntity.getId(), agent.getId());
        assertEquals(agentEntity.getName(), agent.getName());
        assertEquals(agentEntity.getDescription(), agent.getDescription());
        assertEquals("Hello {{name}}", agent.getPrompt());
        assertNotNull(agent.getPromptTemplate());
        assertEquals(templateEntity.getId(), agent.getPromptTemplate().getId());
        assertEquals(templateEntity.getTemplate(), agent.getPromptTemplate().getTemplate());
        assertEquals(agentEntity.getModel(), agent.getModel());
        assertEquals(agentEntity.getProvider(), agent.getProvider());
        assertEquals(agentEntity.getTemperature(), agent.getTemperature());
        assertEquals(agentEntity.isActive(), agent.isActive());
    }

    @Test
    void toDomain_with_nullPromptTemplate_then_promptNull() {
        AgentEntity agentEntity = AgentDataFactory.createAgentEntity();
        agentEntity.setPromptTemplate(null);

        Agent agent = mapper.toDomain(agentEntity);

        assertNotNull(agent);
        assertEquals(null, agent.getPrompt());
        assertEquals(null, agent.getPromptTemplate());
    }

    @Test
    void toEntity() {
        Agent agent = AgentDataFactory.createAgent();
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setCode("CODE");
        template.setName("Template");
        template.setTemplate("Hello {{name}}");
        template.setVersion(1);
        template.setStatus(PromptStatus.DRAFT);
        template.setParams(Set.of("name"));
        agent.setPromptTemplate(template);

        AgentEntity agentEntity = mapper.toEntity(agent);

        assertNotNull(agentEntity);
        assertEquals(agent.getId(), agentEntity.getId());
        assertEquals(agent.getName(), agentEntity.getName());
        assertEquals(agent.getDescription(), agentEntity.getDescription());
        assertNotNull(agentEntity.getPromptTemplate());
        assertEquals(template.getId(), agentEntity.getPromptTemplate().getId());
        assertEquals(template.getTemplate(), agentEntity.getPromptTemplate().getTemplate());
        assertEquals(agent.getModel(), agentEntity.getModel());
        assertEquals(agent.getProvider(), agentEntity.getProvider());
        assertEquals(agent.getTemperature(), agentEntity.getTemperature());
        assertEquals(agent.isActive(), agentEntity.isActive());
    }
}
