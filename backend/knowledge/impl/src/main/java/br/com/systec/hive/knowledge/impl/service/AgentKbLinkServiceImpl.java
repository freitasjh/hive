package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbTooManyLinksException;
import br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException;
import br.com.systec.hive.knowledge.api.port.KbAgentLinkPort;
import br.com.systec.hive.knowledge.api.service.AgentKbLinkService;
import br.com.systec.hive.knowledge.impl.entity.AgentKbEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.repository.AgentKbRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentKbLinkServiceImpl implements AgentKbLinkService, KbAgentLinkPort {

    public static final int MAX_LINKS = 5;

    private static final Logger log = LoggerFactory.getLogger(AgentKbLinkServiceImpl.class);

    private final AgentKbRepository linkRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public AgentKbLinkServiceImpl(AgentKbRepository linkRepository,
                                  KnowledgeBaseRepository knowledgeBaseRepository) {
        this.linkRepository = linkRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    @Override
    @Transactional
    public void link(UUID agentId, UUID kbId, UUID tenantId) {
        KnowledgeBaseEntity kb = knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        if (linkRepository.existsByAgentIdAndKbIdAndTenantId(agentId, kbId, tenantId)) {
            return;
        }
        if (linkRepository.countByAgentIdAndTenantId(agentId, tenantId) >= MAX_LINKS) {
            throw new KbTooManyLinksException(MAX_LINKS);
        }
        AgentKbEntity link = new AgentKbEntity();
        link.setAgentId(agentId);
        link.setKbId(kb.getId());
        link.setTenantId(tenantId);
        linkRepository.save(link);
    }

    @Override
    @Transactional
    public void unlink(UUID agentId, UUID kbId, UUID tenantId) {
        linkRepository.deleteByAgentIdAndKbIdAndTenantId(agentId, kbId, tenantId);
    }

    @Override
    public List<UUID> linkedKbIds(UUID agentId, UUID tenantId) {
        return linkRepository.findKbIdsByAgent(agentId, tenantId);
    }

    @Override
    public List<String> linkedKbNames(UUID agentId, UUID tenantId) {
        List<UUID> ids = linkedKbIds(agentId, tenantId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return knowledgeBaseRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .map(KnowledgeBaseEntity::getName)
                .sorted()
                .toList();
    }

    @Override
    public List<String> agentsUsingKb(UUID kbId, UUID tenantId) {
        return linkRepository.findByKbIdAndTenantId(kbId, tenantId).stream()
                .map(l -> l.getAgentId().toString())
                .toList();
    }

    @Override
    @Transactional
    public void unlinkAllFromKb(UUID kbId, UUID tenantId) {
        linkRepository.deleteByKbIdAndTenantId(kbId, tenantId);
    }

    @Override
    @Transactional
    public void replaceAll(UUID agentId, UUID tenantId, List<UUID> kbIds) {
        List<UUID> distinct = kbIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(kbIds));
        if (distinct.size() > MAX_LINKS) {
            throw new KbTooManyLinksException(MAX_LINKS);
        }
        if (!distinct.isEmpty()) {
            List<KnowledgeBaseEntity> kbs = knowledgeBaseRepository.findByTenantIdAndIdIn(tenantId, distinct);
            if (kbs.size() != distinct.size()) {
                throw new KnowledgeBaseNotFoundException();
            }
            Map<UUID, KnowledgeBaseEntity> byId =
                    kbs.stream().collect(Collectors.toMap(KnowledgeBaseEntity::getId, Function.identity()));
            for (UUID id : distinct) {
                if (!byId.containsKey(id)) {
                    throw new KnowledgeBaseNotFoundException();
                }
            }
        }
        List<AgentKbEntity> current = linkRepository.findByAgentIdAndTenantId(agentId, tenantId);
        for (AgentKbEntity link : current) {
            if (!distinct.contains(link.getKbId())) {
                linkRepository.delete(link);
            }
        }
        for (UUID kbId : distinct) {
            if (!linkRepository.existsByAgentIdAndKbIdAndTenantId(agentId, kbId, tenantId)) {
                AgentKbEntity link = new AgentKbEntity();
                link.setAgentId(agentId);
                link.setKbId(kbId);
                link.setTenantId(tenantId);
                linkRepository.save(link);
            }
        }
        log.info("kb links replaced tenantId={} agentId={} count={}", tenantId, agentId, distinct.size());
    }

    @Override
    public void replaceLinks(UUID agentId, UUID tenantId, List<UUID> kbIds) {
        // Deliberately not @Transactional here: the proxy-based transaction of replaceAll already
        // wraps the whole replace, and self-invocation would bypass the proxy and silently drop it.
        replaceAll(agentId, tenantId, kbIds);
    }
}
