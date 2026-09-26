package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.agent.impl.entity.ChatMessageEntity;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import br.com.systec.hive.agent.impl.mapper.ChatMessageMapperDomain;
import br.com.systec.hive.agent.impl.repository.ChatMessageRepository;
import br.com.systec.hive.agent.impl.repository.ChatSessionRepository;
import br.com.systec.hive.provider.api.ChatRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageMapperDomain mapper;

    @InjectMocks
    private ChatMessageServiceImpl service;

    @Test
    void when_save_with_validSession_then_persistsWithTenantId() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        when(chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)).thenReturn(Optional.of(session));

        ChatMessageEntity saved = new ChatMessageEntity();
        saved.setId(UUID.randomUUID());
        when(chatMessageRepository.save(any(ChatMessageEntity.class))).thenReturn(saved);

        ChatMessage domain = new ChatMessage();
        domain.setContent("hello");
        when(mapper.toDomain(any(ChatMessageEntity.class))).thenReturn(domain);

        ChatMessage result = service.save(sessionId, tenantId, ChatRole.USER, "hello");

        assertThat(result).isNotNull();
        verify(chatMessageRepository).save(any(ChatMessageEntity.class));
    }

    @Test
    void when_save_with_nonExistingSession_then_throwNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(sessionId, tenantId, ChatRole.USER, "hello"))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_findLast20_with_25Messages_then_returnsLast20Asc() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        List<ChatMessageEntity> entitiesDesc = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ChatMessageEntity e = new ChatMessageEntity();
            e.setContent("msg" + i);
            e.setRole(ChatRole.USER);
            ChatSessionEntity s = new ChatSessionEntity();
            s.setId(sessionId);
            e.setSession(s);
            entitiesDesc.add(e);
        }
        // Simulate DESC order: newest first
        when(chatMessageRepository.findBySessionIdAndTenantIdOrderByCreateAtDesc(eq(sessionId), eq(tenantId), any(Pageable.class)))
                .thenReturn(entitiesDesc);

        for (ChatMessageEntity e : entitiesDesc) {
            ChatMessage dm = new ChatMessage();
            dm.setContent(e.getContent());
            when(mapper.toDomain(e)).thenReturn(dm);
        }

        List<ChatMessage> result = service.findLast20(sessionId, tenantId);

        assertThat(result).hasSize(20);
        verify(chatMessageRepository).findBySessionIdAndTenantIdOrderByCreateAtDesc(eq(sessionId), eq(tenantId), any(Pageable.class));
    }

    @Test
    void when_getMessages_with_pagination_then_returnsAsc() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        when(chatSessionRepository.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)).thenReturn(Optional.of(session));

        ChatMessageEntity e = new ChatMessageEntity();
        e.setContent("hello");
        Page<ChatMessageEntity> page = new PageImpl<>(List.of(e));
        when(chatMessageRepository.findBySessionIdAndTenantIdOrderByCreateAtAsc(eq(sessionId), eq(tenantId), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toDomain(any(ChatMessageEntity.class))).thenReturn(new ChatMessage());

        Pageable pageable = PageRequest.of(0, 50);
        Page<ChatMessage> result = service.getMessages(sessionId, tenantId, userId, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(chatMessageRepository).findBySessionIdAndTenantIdOrderByCreateAtAsc(eq(sessionId), eq(tenantId), any(Pageable.class));
    }

    @Test
    void when_getMessages_with_wrongUser_then_throwNotFound() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(chatSessionRepository.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)).thenReturn(Optional.empty());

        Pageable pageable = PageRequest.of(0, 50);
        assertThatThrownBy(() -> service.getMessages(sessionId, tenantId, userId, pageable))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_save_with_contentExceeds4000_then_throwValidation() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String longContent = "x".repeat(4001);

        assertThatThrownBy(() -> service.save(sessionId, tenantId, ChatRole.USER, longContent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_findLast20_verify_pageRequest_20_desc() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(chatMessageRepository.findBySessionIdAndTenantIdOrderByCreateAtDesc(eq(sessionId), eq(tenantId), any(Pageable.class)))
                .thenReturn(List.of());

        service.findLast20(sessionId, tenantId);

        verify(chatMessageRepository).findBySessionIdAndTenantIdOrderByCreateAtDesc(eq(sessionId), eq(tenantId), any(Pageable.class));
    }
}
