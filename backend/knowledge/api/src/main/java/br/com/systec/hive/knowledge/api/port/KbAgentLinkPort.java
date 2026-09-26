package br.com.systec.hive.knowledge.api.port;

import java.util.List;
import java.util.UUID;

public interface KbAgentLinkPort {

    List<UUID> linkedKbIds(UUID agentId, UUID tenantId);

    List<String> linkedKbNames(UUID agentId, UUID tenantId);

    /**
     * Replaces the whole set of knowledge bases linked to an agent. An empty list unlinks everything.
     *
     * <p>Write side of the {@code agent -> knowledge} boundary. It lives here, and not in the agent
     * module, because {@code agent_knowledge_base} is a table of the knowledge module: only this side
     * may decide whether a given knowledge base exists, belongs to the tenant, and how many links an
     * agent may hold (RN-KB4). The caller owns the <b>authorization</b> question — is this agent
     * mine? — and must answer it before calling.
     *
     * <p>Implementations are transactional: on failure the agent keeps its previous link set rather
     * than a partially replaced one.
     *
     * @throws br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException if any id
     *         does not exist or does not belong to {@code tenantId}
     * @throws br.com.systec.hive.knowledge.api.exceptions.KbTooManyLinksException if more than
     *         {@code MAX_LINKS} distinct knowledge bases are given
     */
    void replaceLinks(UUID agentId, UUID tenantId, List<UUID> kbIds);
}
