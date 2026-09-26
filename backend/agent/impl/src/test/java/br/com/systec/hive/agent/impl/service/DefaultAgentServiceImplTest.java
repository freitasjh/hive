package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.exceptions.DefaultAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.InactiveAgentCannotBeDefaultException;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.agent.impl.mapper.AgentMapperDomain;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DefaultAgentServiceImplTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentMapperDomain mapper;

    @InjectMocks
    private DefaultAgentServiceImpl service;

    @Test
    void when_findDefault_with_existing_then_returns() {
        UUID tenantId = UUID.randomUUID();
        AgentEntity entity = new AgentEntity();
        entity.setId(UUID.randomUUID());
        entity.setActive(true);
        entity.setDefault(true);
        Agent domain = new Agent();
        when(agentRepository.findByTenantIdAndIsDefaultTrueAndActiveTrue(tenantId)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        var result = service.findDefault(tenantId);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(domain);
    }

    @Test
    void when_requireDefault_with_empty_then_throws422() {
        UUID tenantId = UUID.randomUUID();
        when(agentRepository.findByTenantIdAndIsDefaultTrueAndActiveTrue(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireDefault(tenantId))
                .isInstanceOf(DefaultAgentNotFoundException.class);
    }

    @Test
    void when_setDefault_with_inactive_then_throws422() {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = new AgentEntity();
        entity.setId(agentId);
        entity.setActive(false);
        when(agentRepository.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.setDefault(tenantId, agentId))
                .isInstanceOf(InactiveAgentCannotBeDefaultException.class);
        verify(agentRepository, never()).clearDefaultByTenantId(any());
        verify(agentRepository, never()).setDefaultById(any(), any());
    }

    @Test
    void when_setDefault_with_active_then_callsUpdateFlag() {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = new AgentEntity();
        entity.setId(agentId);
        entity.setActive(true);
        AgentEntity refreshed = new AgentEntity();
        refreshed.setId(agentId);
        refreshed.setDefault(true);
        Agent domain = new Agent();
        domain.setId(agentId);

        when(agentRepository.findByIdAndTenantId(agentId, tenantId))
                .thenReturn(Optional.of(entity))
                .thenReturn(Optional.of(refreshed));
        when(agentRepository.clearDefaultByTenantId(tenantId)).thenReturn(1);
        when(agentRepository.setDefaultById(tenantId, agentId)).thenReturn(1);
        when(mapper.toDomain(refreshed)).thenReturn(domain);

        Agent result = service.setDefault(tenantId, agentId);

        assertThat(result.getId()).isEqualTo(agentId);
        verify(agentRepository).clearDefaultByTenantId(tenantId);
        verify(agentRepository).setDefaultById(tenantId, agentId);
    }
}
