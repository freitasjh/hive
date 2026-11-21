package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.history.impl.repository.ChatMessageRepository;
import br.com.systec.hive.history.impl.repository.ChatSessionRepository;
import br.com.systec.hive.history.impl.entity.ChatMessageEntity;
import br.com.systec.hive.history.impl.entity.ChatSessionEntity;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.provider.api.ChatRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatAssistantTxService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;

    public ChatAssistantTxService(ChatMessageRepository chatMessageRepository,
                                  ChatSessionRepository chatSessionRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Transactional
    public ChatMessageEntity saveAssistantAndTouch(UUID sessionId, UUID tenantId, String content) {
        ChatSessionEntity session = chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSession(session);
        entity.setRole(ChatRole.ASSISTANT);
        entity.setContent(content);
        entity.setTenantId(tenantId);
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        session.setUpdateAt(LocalDateTime.now());
        chatSessionRepository.save(session);
        return saved;
    }
}
