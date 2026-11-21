package br.com.systec.hive.history.impl.service;

import br.com.systec.hive.assistant.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.history.api.service.ChatMessageService;
import br.com.systec.hive.history.impl.entity.ChatMessageEntity;
import br.com.systec.hive.history.impl.entity.ChatSessionEntity;
import br.com.systec.hive.history.impl.mapper.ChatMessageMapperDomain;
import br.com.systec.hive.history.impl.repository.ChatMessageRepository;
import br.com.systec.hive.history.impl.repository.ChatSessionRepository;
import br.com.systec.hive.provider.api.ChatRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageMapperDomain mapper;

    public ChatMessageServiceImpl(ChatMessageRepository chatMessageRepository,
                                  ChatSessionRepository chatSessionRepository,
                                  ChatMessageMapperDomain mapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ChatMessage save(UUID sessionId, UUID tenantId, ChatRole role, String content) {
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("content must be between 1 and 4000 characters");
        }
        ChatSessionEntity session = chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSession(session);
        entity.setRole(role);
        entity.setContent(content);
        entity.setTenantId(tenantId);

        ChatMessageEntity saved = chatMessageRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Page<ChatMessage> getMessages(UUID sessionId, UUID tenantId, UUID userId, Pageable pageable) {
        chatSessionRepository.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));

        PageRequest sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "createAt"));
        Page<ChatMessageEntity> page = chatMessageRepository.findBySessionIdAndTenantIdOrderByCreateAtAsc(sessionId, tenantId, sorted);
        return page.map(mapper::toDomain);
    }

    @Override
    public List<ChatMessage> findLast20(UUID sessionId, UUID tenantId) {
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createAt"));
        List<ChatMessageEntity> entities = chatMessageRepository.findBySessionIdAndTenantIdOrderByCreateAtDesc(sessionId, tenantId, pageRequest);
        Collections.reverse(entities);
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countBySessionIdAndTenantId(UUID sessionId, UUID tenantId) {
        return chatMessageRepository.countBySessionIdAndTenantId(sessionId, tenantId);
    }
}
