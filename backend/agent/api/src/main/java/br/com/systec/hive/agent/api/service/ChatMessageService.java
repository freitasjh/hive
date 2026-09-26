package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.provider.api.ChatRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ChatMessageService {

    ChatMessage save(UUID sessionId, UUID tenantId, ChatRole role, String content);

    Page<ChatMessage> getMessages(UUID sessionId, UUID tenantId, UUID userId, Pageable pageable);

    List<ChatMessage> findLast20(UUID sessionId, UUID tenantId);

    long countBySessionIdAndTenantId(UUID sessionId, UUID tenantId);
}
