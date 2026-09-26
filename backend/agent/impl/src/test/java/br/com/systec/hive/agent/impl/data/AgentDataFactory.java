package br.com.systec.hive.agent.impl.data;

import br.com.systec.hive.agent.api.domain.Agent;

import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

public class AgentDataFactory {

    public static AgentEntity createAgentEntity() {
        AgentEntity agentEntity = new AgentEntity();
        agentEntity.setId(UUID.randomUUID());
        agentEntity.setName("Test Agent");
        agentEntity.setDescription("Test Description");
        agentEntity.setProvider(AiProvider.OLLAMA);
        agentEntity.setModel("llama3.2");
        agentEntity.setTemperature(0.5);
        agentEntity.setActive(true);
        return agentEntity;
    }

    public static Agent createAgent() {
        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("Test Agent");
        agent.setDescription("Test Description");
        agent.setPrompt("Test Prompt");
        agent.setProvider(AiProvider.OLLAMA);
        agent.setModel("llama3.2");
        agent.setTemperature(0.5);
        agent.setActive(true);
        return agent;
    }
}
