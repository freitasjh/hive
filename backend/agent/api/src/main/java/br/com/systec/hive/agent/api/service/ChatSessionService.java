package br.com.systec.hive.agent.api.service;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ChatSessionService {

    ChatSession create(UUID promptTemplateId, String title, UUID tenantId, UUID userId);

    Page<ChatSession> listSessions(UUID tenantId, UUID userId, Pageable pageable);

    ChatSession getSession(UUID id, UUID tenantId, UUID userId);

    /**
     * The template this session is answered by, resolved through the session's own row.
     *
     * <p>Going through the session rather than taking an id is the point: the id a client sends is
     * the id it <em>asked</em> for, and the id the session holds is the one that was agreed when the
     * conversation started. Resolving through the row is what makes the second one authoritative,
     * and taking the tenant explicitly keeps the read off {@code TenantContext}, which is not bound
     * in every caller.
     *
     * @return {@code null} only if the session's template row is gone
     */
    PromptTemplate resolvePromptTemplate(UUID sessionId, UUID tenantId, UUID userId);

    void deleteSession(UUID id, UUID tenantId, UUID userId);

    void touchUpdateAt(UUID sessionId, UUID tenantId);

    ChatSession updateTitle(UUID id, String title, UUID tenantId, UUID userId);
}
