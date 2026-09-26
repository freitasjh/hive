package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.exceptions.AgentNotFoundException;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import br.com.systec.hive.agent.impl.mapper.AgentMapperDomain;
import br.com.systec.hive.agent.impl.mapper.PromptTemplateDomainMapper;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import br.com.systec.hive.agent.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    @Mock
    private AgentRepository repository;

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @Mock
    private AgentMapperDomain mapper;

    @Mock
    private PromptTemplateDomainMapper promptMapper;

    @InjectMocks
    private AgentServiceImpl service;

    @Test
    void when_create_then_success() {
        Agent agent = new Agent();
        agent.setActive(true);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENAI);
        agent.setModel("gpt-4o-mini");
        AgentEntity agentEntity = new AgentEntity();
        AgentEntity agentSaved = new AgentEntity();
        Agent agentResult = new Agent();

        when(mapper.toEntity(agent)).thenReturn(agentEntity);
        when(repository.save(agentEntity)).thenReturn(agentSaved);
        when(mapper.toDomain(agentSaved)).thenReturn(agentResult);

        Agent result = service.create(agent);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(agentResult);
        verify(repository).save(agentEntity);
    }

    @Test
    void when_create_with_modelOutsideCatalog_then_throwsModelNotSupported() {
        Agent agent = new Agent();
        agent.setActive(true);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENAI);
        agent.setModel("llama3.2");

        assertThatThrownBy(() -> service.create(agent))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void when_update_withExistingAgent_then_success() {
        UUID id = UUID.randomUUID();
        Agent agent = new Agent();
        agent.setId(id);
        agent.setActive(true);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENAI);
        agent.setModel("gpt-4o-mini");
        AgentEntity agentEntity = new AgentEntity();
        agentEntity.setActive(true);
        agentEntity.setDefault(false);
        AgentEntity agentSaved = new AgentEntity();
        Agent agentResult = new Agent();

        when(repository.findById(id)).thenReturn(Optional.of(agentEntity));
        when(repository.save(agentEntity)).thenReturn(agentSaved);
        when(mapper.toDomain(agentSaved)).thenReturn(agentResult);

        Agent result = service.update(agent);

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(agentResult);
        verify(mapper).updateEntity(agentEntity, agent);
        verify(repository).save(agentEntity);
    }

    @Test
    void when_update_withNonExistingAgent_then_throwException() {
        UUID id = UUID.randomUUID();
        Agent agent = new Agent();
        agent.setId(id);

        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AgentNotFoundException.class, () -> service.update(agent));
    }

    @Test
    void when_update_withOmittedIsDefault_then_preservesDefault() {
        UUID id = UUID.randomUUID();
        Agent incoming = new Agent();
        incoming.setId(id);
        incoming.setName("Updated");
        // simulate PUT with omitted isDefault/active -> mapper leaves null
        incoming.setIsDefault(null);
        incoming.setActive(null);

        AgentEntity existing = new AgentEntity();
        existing.setId(id);
        existing.setName("Old");
        existing.setDefault(true);
        existing.setActive(true);

        AgentEntity saved = new AgentEntity();
        saved.setId(id);
        saved.setDefault(true);
        saved.setActive(true);
        Agent resultDomain = new Agent();
        resultDomain.setId(id);
        resultDomain.setDefault(true);

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(resultDomain);

        Agent result = service.update(incoming);

        assertThat(result.isDefault()).isTrue();
        assertThat(existing.isDefault()).isTrue();
        assertThat(existing.isActive()).isTrue();
        verify(mapper).updateEntity(existing, incoming);
        verify(repository).save(existing);
    }

    @Test
    void when_update_withOmittedActive_then_preservesActive() {        UUID id = UUID.randomUUID();
        Agent incoming = new Agent();
        incoming.setId(id);
        incoming.setIsDefault(null);
        incoming.setActive(null);

        AgentEntity existing = new AgentEntity();
        existing.setId(id);
        existing.setDefault(false);
        existing.setActive(true);

        AgentEntity saved = new AgentEntity();
        saved.setId(id);
        Agent resultDomain = new Agent();

        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(saved);
        when(mapper.toDomain(saved)).thenReturn(resultDomain);

        service.update(incoming);

        assertThat(existing.isActive()).isTrue();
        assertThat(existing.isDefault()).isFalse();
    }

    @Test
    void when_create_with_goActiveWithoutCredential_then_throws422() {
        CredentialResolver resolver = mock(CredentialResolver.class);
        service.setCredentialResolver(resolver);
        UUID tenantId = UUID.randomUUID();
        Agent agent = new Agent();
        agent.setActive(true);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENCODE_GO);
        agent.setModel("mimo-v2.5");
        when(resolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenThrow(new br.com.systec.hive.agent.api.exceptions.ConfigMissingException("OPENCODE_GO"));

        assertThatThrownBy(() -> br.com.systec.hive.commons.impl.security.TenantContext
                .runWithTenant(tenantId, () -> {
                    service.create(agent);
                    return null;
                })).isInstanceOf(br.com.systec.hive.agent.api.exceptions.ConfigMissingException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void when_create_with_goInactiveWithoutCredential_then_allowed() throws Exception {
        CredentialResolver resolver = mock(CredentialResolver.class);
        service.setCredentialResolver(resolver);
        UUID tenantId = UUID.randomUUID();
        Agent agent = new Agent();
        agent.setActive(false);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENCODE_GO);
        agent.setModel("mimo-v2.5");
        AgentEntity agentEntity = new AgentEntity();
        AgentEntity agentSaved = new AgentEntity();
        when(mapper.toEntity(agent)).thenReturn(agentEntity);
        when(repository.save(agentEntity)).thenReturn(agentSaved);
        when(mapper.toDomain(agentSaved)).thenReturn(new Agent());

        br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () -> {
            service.create(agent);
            return null;
        });

        verify(resolver, never()).resolve(any(), any());
        verify(repository).save(agentEntity);
    }

    @Test
    void when_create_with_goActiveAndCredential_then_success() throws Exception {
        CredentialResolver resolver = mock(CredentialResolver.class);
        service.setCredentialResolver(resolver);
        UUID tenantId = UUID.randomUUID();
        Agent agent = new Agent();
        agent.setActive(true);
        agent.setDefault(false);
        agent.setProvider(AiProvider.OPENCODE_ZEN);
        agent.setModel("claude-sonnet-4-5");
        when(resolver.resolve(tenantId, AiProvider.OPENCODE_ZEN))
                .thenReturn(new CredentialResolver.ResolvedCredential("zen-fake", null));
        AgentEntity agentEntity = new AgentEntity();
        AgentEntity agentSaved = new AgentEntity();
        when(mapper.toEntity(agent)).thenReturn(agentEntity);
        when(repository.save(agentEntity)).thenReturn(agentSaved);
        when(mapper.toDomain(agentSaved)).thenReturn(new Agent());

        br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () -> {
            service.create(agent);
            return null;
        });

        verify(resolver).resolve(tenantId, AiProvider.OPENCODE_ZEN);
        verify(repository).save(agentEntity);
    }
}
