package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.exceptions.ChatAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import br.com.systec.hive.agent.impl.mapper.ChatSessionMapperDomain;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import br.com.systec.hive.agent.impl.repository.ChatSessionRepository;
import br.com.systec.hive.agent.impl.entity.AgentEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private ChatSessionMapperDomain mapper;

    @InjectMocks
    private ChatSessionServiceImpl service;

    @Test
    void when_create_with_validAgent_then_successAndTitleTruncated50() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String longTitle = "a".repeat(60);

        AgentEntity agentEntity = new AgentEntity();
        agentEntity.setId(agentId);
        when(agentRepository.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(agentEntity));

        ChatSessionEntity savedEntity = new ChatSessionEntity();
        savedEntity.setId(UUID.randomUUID());
        savedEntity.setTitle(longTitle.substring(0, 50));
        when(chatSessionRepository.save(any(ChatSessionEntity.class))).thenReturn(savedEntity);

        ChatSession domain = new ChatSession();
        domain.setId(savedEntity.getId());
        when(mapper.toDomain(savedEntity)).thenReturn(domain);

        ChatSession result = service.create(agentId, longTitle, tenantId, userId);

        assertThat(result).isNotNull();
        ArgumentCaptor<ChatSessionEntity> captor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(50);
        assertThat(captor.getValue().getTitle()).doesNotContain("...");
    }

    @Test
    void when_create_with_nonExistingAgent_then_throwAgentNotFound() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(agentRepository.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(agentId, "title", tenantId, userId))
                .isInstanceOf(ChatAgentNotFoundException.class);
    }

    @Test
    void when_create_with_titleLongerThan50_then_truncatesWithoutEllipsis() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String title51 = "x".repeat(51);
        when(agentRepository.findByIdAndTenantId(agentId, tenantId)).thenReturn(Optional.of(new AgentEntity()));
        ChatSessionEntity saved = new ChatSessionEntity();
        saved.setId(UUID.randomUUID());
        when(chatSessionRepository.save(any())).thenReturn(saved);
        when(mapper.toDomain(any())).thenReturn(new ChatSession());

        service.create(agentId, title51, tenantId, userId);

        ArgumentCaptor<ChatSessionEntity> captor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(50);
        assertThat(captor.getValue().getTitle()).isEqualTo("x".repeat(50));
    }

    @Test
    void when_listSessions_with_tenantAndUser_then_filtersAndSortsByUpdateAtDesc() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(UUID.randomUUID());
        Page<ChatSessionEntity> entityPage = new PageImpl<>(List.of(entity));
        when(chatSessionRepository.findByTenantIdAndUserId(any(), any(), any(Pageable.class))).thenReturn(entityPage);
        when(mapper.toDomain(any(ChatSessionEntity.class))).thenReturn(new ChatSession());

        Page<ChatSession> result = service.listSessions(tenantId, userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(chatSessionRepository).findByTenantIdAndUserId(any(), any(), pageableCaptor.capture());
        Pageable used = pageableCaptor.getValue();
        assertThat(used.getSort().getOrderFor("updateAt")).isNotNull();
        assertThat(used.getSort().getOrderFor("updateAt").getDirection().name()).isEqualTo("DESC");
    }

    @Test
    void when_getSession_with_otherTenant_then_throwNotFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(id, tenantId, userId))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_delete_with_existingSession_then_deletesAndCascade() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(id);
        when(chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)).thenReturn(Optional.of(entity));

        service.deleteSession(id, tenantId, userId);

        verify(chatSessionRepository).deleteByIdAndTenantId(id, tenantId);
    }

    @Test
    void when_delete_with_nonExistingSession_then_throwNotFoundIdempotent() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSession(id, tenantId, userId))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_touchUpdateAt_with_validSession_then_updates() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setId(sessionId);
        entity.setUpdateAt(LocalDateTime.now().minusDays(1));
        when(chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)).thenReturn(Optional.of(entity));
        when(chatSessionRepository.save(any())).thenReturn(entity);

        service.touchUpdateAt(sessionId, tenantId);

        verify(chatSessionRepository).save(any(ChatSessionEntity.class));
    }
}
