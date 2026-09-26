package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.exceptions.DefaultAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.SoleDefaultAgentException;
import br.com.systec.hive.agent.api.service.AgentService;
import br.com.systec.hive.agent.api.service.DefaultAgentService;
import br.com.systec.hive.agent.api.service.ValidateService;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentCreateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.AgentDTOMapper;
import br.com.systec.hive.commons.api.tools.exceptions.ControllerExceptionHandler;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerV1Test {

    @Mock
    AgentService agentService;
    @Mock
    DefaultAgentService defaultAgentService;
    @Mock
    ValidateService validateService;
    @Mock
    AgentDTOMapper mapper;

    @InjectMocks
    AgentControllerV1 controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    private Agent dummyAgent(UUID id, boolean isDefault) {
        Agent a = new Agent();
        a.setId(id);
        a.setName("Assistente");
        a.setProvider(AiProvider.OPENAI);
        a.setModel("gpt-4o-mini");
        a.setDefault(isDefault);
        a.setActive(true);
        return a;
    }

    private AgentDTO dummyDTO(UUID id, boolean isDefault) {
        return new AgentDTO(id, "Assistente", "https://cdn.example.com/avatar.png", "desc", "systemPrompt",
                null, "gpt-4o-mini", AiProvider.OPENAI, 0.8, 0.9, 2048, List.of("web_search"), List.of("kb"), isDefault, true,
                java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    void when_create_with_valid_then_201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Agent domain = dummyAgent(id, false);
        AgentDTO dto = dummyDTO(id, false);
        when(mapper.toDomain(any(AgentCreateDTO.class))).thenReturn(domain);
        when(agentService.create(any(Agent.class))).thenReturn(domain);
        when(mapper.toDto(any(Agent.class))).thenReturn(dto);
        String body = """
                {"name":"Assistente","model":"gpt-4o-mini","provider":"OPENAI"}
                """;
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(post("/v1/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated());
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_create_with_invalidAvatar_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String body = """
                {"name":"Assistente","model":"gpt-4o-mini","provider":"OPENAI","avatarUrl":"not-a-url"}
                """;
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(post("/v1/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_list_with_valid_then_200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Agent domain = dummyAgent(UUID.randomUUID(), false);
        Page<Agent> page = new PageImpl<>(List.of(domain));
        when(agentService.listAgents(eq(tenantId), any(), isNull())).thenReturn(page);
        when(mapper.toDto(any(Agent.class))).thenReturn(dummyDTO(domain.getId(), false));
        TenantContext.runWithTenant(tenantId, () -> {
            var result = controller.list(0, 20, null);
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).isNotNull();
            return null;
        });
    }

    @Test
    void when_getDefault_withoutDefault_then_404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(defaultAgentService.getDefault(eq(tenantId))).thenReturn(Optional.empty());
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/agents/default"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("DEFAULT_AGENT_NOT_FOUND"));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_setDefault_with_valid_then_200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Agent domain = dummyAgent(id, true);
        when(defaultAgentService.setDefault(eq(tenantId), eq(id))).thenReturn(domain);
        when(mapper.toDto(any(Agent.class))).thenReturn(dummyDTO(id, true));
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(post("/v1/agents/" + id + "/default"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.isDefault").value(true));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_delete_soleDefault_then_409() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        doThrow(new SoleDefaultAgentException()).when(agentService).delete(any(Agent.class));
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(delete("/v1/agents/" + id))
                        .andExpect(status().isConflict())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }
}
