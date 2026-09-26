package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.AgentValidation;
import br.com.systec.hive.agent.api.exceptions.DefaultAgentNotFoundException;
import br.com.systec.hive.agent.api.exceptions.KnowledgeModuleUnavailableException;
import br.com.systec.hive.agent.api.service.AgentService;
import br.com.systec.hive.agent.api.service.DefaultAgentService;
import br.com.systec.hive.agent.api.service.ValidateService;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentCreateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentKnowledgeBasesDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentKnowledgeBasesDTOResponse;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentKnowledgePatchDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.AgentUpdateDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.ValidateRequestDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.ValidateResponseDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.AgentDTOMapper;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/agents")
@Tag(name = "Hive Agent - Agents", description = "Gestão de agentes")
public class AgentControllerV1 {

    private final AgentService agentService;
    private final DefaultAgentService defaultAgentService;
    private final ValidateService validateService;
    private final AgentDTOMapper mapper;
    private final br.com.systec.hive.knowledge.api.service.AgentKbLinkService agentKbLinkService;
    private final br.com.systec.hive.knowledge.api.port.KbAgentLinkPort kbAgentLinkPort;

    public AgentControllerV1(AgentService agentService, DefaultAgentService defaultAgentService,
                             ValidateService validateService, AgentDTOMapper mapper,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             br.com.systec.hive.knowledge.api.service.AgentKbLinkService agentKbLinkService,
                             @org.springframework.beans.factory.annotation.Autowired(required = false)
                             br.com.systec.hive.knowledge.api.port.KbAgentLinkPort kbAgentLinkPort) {
        this.agentService = agentService;
        this.defaultAgentService = defaultAgentService;
        this.validateService = validateService;
        this.mapper = mapper;
        this.agentKbLinkService = agentKbLinkService;
        this.kbAgentLinkPort = kbAgentLinkPort;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria um novo agente")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> create(@RequestBody @Valid AgentCreateDTO payload) {
        Agent domain = mapper.toDomain(payload);
        domain.setTenantId(currentTenantId());
        Agent saved = agentService.create(domain);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(saved));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista agentes paginado")
    @HasPermission(anyOf = {SecurityConstants.CHAT_SEND, SecurityConstants.AGENT_MANAGE})
    public ResponseEntity<Page<AgentDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        if (size > 50) {
            throw new IllegalArgumentException("size must be <= 50");
        }
        if (size <= 0) size = 20;
        UUID tenantId = currentTenantId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateAt"));
        Page<Agent> result = agentService.listAgents(tenantId, pageable, q);
        Page<AgentDTO> dtoPage = result.map(mapper::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    @GetMapping(value = "/default", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Retorna agente padrão do tenant")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> getDefault() {
        UUID tenantId = currentTenantId();
        Agent agent = defaultAgentService.getDefault(tenantId)
                .orElseThrow(() -> new DefaultAgentNotFoundException(false));
        return ResponseEntity.ok(mapper.toDto(agent));
    }

    @PostMapping(value = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Testa conexão com o vendor sem persistir nada")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<ValidateResponseDTO> validate(@RequestBody @Valid ValidateRequestDTO payload) {
        UUID tenantId = currentTenantId();
        AgentValidation validation = validateService.validate(tenantId, payload.provider(), payload.model(), payload.baseUrl());
        return ResponseEntity.ok(new ValidateResponseDTO(validation.isOk(), validation.getProvider(),
                validation.getModel(), validation.getLatencyMs(), validation.getReason()));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Busca agente por ID")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> findById(@PathVariable UUID id) {
        Agent domain = agentService.findById(id);
        return ResponseEntity.ok(mapper.toDto(domain));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Atualiza agente")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> update(@PathVariable UUID id, @RequestBody @Valid AgentUpdateDTO payload) {
        Agent domain = mapper.toDomain(payload);
        domain.setId(id);
        domain.setTenantId(currentTenantId());
        Agent updated = agentService.update(domain);
        return ResponseEntity.ok(mapper.toDto(updated));
    }

    @DeleteMapping(value = "/{id}")
    @Operation(description = "Remove agente")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        Agent toDelete = new Agent();
        toDelete.setId(id);
        toDelete.setTenantId(currentTenantId());
        agentService.delete(toDelete);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/default", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Define agente como padrão")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> setDefault(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        Agent agent = defaultAgentService.setDefault(tenantId, id);
        return ResponseEntity.ok(mapper.toDto(agent));
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Vincula KBs ao agente (max 5, por id)")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentDTO> patchKnowledgeBases(@PathVariable UUID id,
                                                        @RequestBody @Valid AgentKnowledgePatchDTO payload) {
        UUID tenantId = currentTenantId();
        agentService.findById(id);
        if (agentKbLinkService != null) {
            agentKbLinkService.replaceAll(id, tenantId, payload.knowledgeBaseIds());
        }
        return ResponseEntity.ok(mapper.toDto(agentService.findById(id)));
    }

    /**
     * Canonical write path for the agent↔knowledge-base link (RN-KB4, max 5).
     *
     * <p>It lives in the agent module, and not next to the link table in {@code knowledge}, because
     * only this side can answer "is this agent mine?". {@code agentService.findById} is tenant-scoped
     * and raises 404 for an agent of another tenant, which is what keeps the write from being an
     * IDOR: the knowledge-side port trusts {@code agentId} and would happily insert a link row
     * pointing at someone else's agent.
     */
    @PutMapping(value = "/{id}/knowledge-bases", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Substitui as KBs vinculadas ao agente (max 5; lista vazia desvincula)")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<AgentKnowledgeBasesDTOResponse> replaceKnowledgeBases(
            @PathVariable UUID id, @RequestBody @Valid AgentKnowledgeBasesDTO payload) {
        UUID tenantId = currentTenantId();
        agentService.findById(id);
        if (kbAgentLinkPort == null) {
            // Módulo de knowledge ausente: degradar para 200 sem vínculos mentiria para o cliente,
            // que acabou de receber um 200 de "substituí seus vínculos".
            throw new KnowledgeModuleUnavailableException();
        }
        kbAgentLinkPort.replaceLinks(id, tenantId, payload.kbIds());
        List<UUID> kbIds = kbAgentLinkPort.linkedKbIds(id, tenantId);
        return ResponseEntity.ok(new AgentKnowledgeBasesDTOResponse(id, kbIds,
                kbAgentLinkPort.linkedKbNames(id, tenantId)));
    }

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getTenantId() != null) return user.getTenantId();
        }
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
