package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.exceptions.ChatAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import br.com.systec.hive.agent.impl.mapper.ChatSessionMapperDomain;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import br.com.systec.hive.agent.impl.repository.ChatSessionRepository;
import br.com.systec.hive.agent.impl.util.ChatTitleUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final AgentRepository agentRepository;
    private final ChatSessionMapperDomain mapper;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository,
                                  AgentRepository agentRepository,
                                  ChatSessionMapperDomain mapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.agentRepository = agentRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ChatSession create(UUID agentId, String title, UUID tenantId, UUID userId) {
        agentRepository.findByIdAndTenantId(agentId, tenantId)
                .orElseThrow(() -> new ChatAgentNotFoundException(agentId));

        String truncatedTitle = ChatTitleUtil.truncate(title, 50);

        ChatSessionEntity entity = new ChatSessionEntity();
        entity.setUserId(userId);
        entity.setAgentId(agentId);
        entity.setTitle(truncatedTitle);
        entity.setTenantId(tenantId);

        ChatSessionEntity saved = chatSessionRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Page<ChatSession> listSessions(UUID tenantId, UUID userId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updateAt"));
        Page<ChatSessionEntity> page = chatSessionRepository.findByTenantIdAndUserId(tenantId, userId, sorted);
        return page.map(mapper::toDomain);
    }

    @Override
    public ChatSession getSession(UUID id, UUID tenantId, UUID userId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public void deleteSession(UUID id, UUID tenantId, UUID userId) {
        chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        chatSessionRepository.deleteByIdAndTenantId(id, tenantId);
    }

    @Override
    @Transactional
    public void touchUpdateAt(UUID sessionId, UUID tenantId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ChatSessionNotFoundException(sessionId));
        entity.setUpdateAt(LocalDateTime.now());
        chatSessionRepository.save(entity);
    }

    @Override
    @Transactional
    public ChatSession updateTitle(UUID id, String title, UUID tenantId, UUID userId) {
        ChatSessionEntity entity = chatSessionRepository.findByIdAndTenantIdAndUserId(id, tenantId, userId)
                .orElseThrow(() -> new ChatSessionNotFoundException(id));
        String truncated = ChatTitleUtil.truncate(title, 50);
        entity.setTitle(truncated);
        entity.setUpdateAt(LocalDateTime.now());
        ChatSessionEntity saved = chatSessionRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
