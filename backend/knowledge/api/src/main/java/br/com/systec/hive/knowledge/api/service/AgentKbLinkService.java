package br.com.systec.hive.knowledge.api.service;

import java.util.List;
import java.util.UUID;

public interface AgentKbLinkService {

    void link(UUID agentId, UUID kbId, UUID tenantId);

    void unlink(UUID agentId, UUID kbId, UUID tenantId);

    List<UUID> linkedKbIds(UUID agentId, UUID tenantId);

    List<String> linkedKbNames(UUID agentId, UUID tenantId);

    List<String> agentsUsingKb(UUID kbId, UUID tenantId);

    void unlinkAllFromKb(UUID kbId, UUID tenantId);

    void replaceAll(UUID agentId, UUID tenantId, List<UUID> kbIds);
}
