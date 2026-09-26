package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ChatSessionService {

    ChatSession create(UUID agentId, String title, UUID tenantId, UUID userId);

    Page<ChatSession> listSessions(UUID tenantId, UUID userId, Pageable pageable);

    ChatSession getSession(UUID id, UUID tenantId, UUID userId);

    void deleteSession(UUID id, UUID tenantId, UUID userId);

    void touchUpdateAt(UUID sessionId, UUID tenantId);

    ChatSession updateTitle(UUID id, String title, UUID tenantId, UUID userId);
}
