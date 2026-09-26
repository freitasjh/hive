package br.com.systec.hive.knowledge.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.knowledge.api.service.AgentKbLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view of the agent↔knowledge-base links from the knowledge side.
 *
 * <p><b>The write lives in {@code AgentControllerV1} ({@code PUT /v1/agents/{id}/knowledge-bases}),
 * not here.</b> Two reasons, and the first is a security one:
 * <ol>
 *   <li>Answering "is this agent mine?" needs the agent aggregate, which this module cannot see.
 *       A write here accepted any {@code agentId} and inserted a link row pointing at another
 *       tenant's agent — API1 (Broken Object Level Authorization) even though the row was written
 *       under the caller's own tenant.</li>
 *   <li>Two owners of the same path+verb is an ambiguous mapping, which fails the context at
 *       startup rather than at request time.</li>
 * </ol>
 * Reading needs no such check: the query is already scoped to the caller's tenant.
 */
@RestController
@RequestMapping(RestPath.V1 + "/agents/{agentId}/knowledge-bases")
@Tag(name = "Hive Knowledge - Vínculos", description = "Consulta dos vínculos agente↔bases (max 5)")
public class AgentKnowledgeLinkControllerV1 {

    private final AgentKbLinkService linkService;

    public AgentKnowledgeLinkControllerV1(AgentKbLinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista KBs vinculadas ao agente")
    @HasPermission(anyOf = {SecurityConstants.KNOWLEDGE_READ, SecurityConstants.KNOWLEDGE_MANAGE})
    public ResponseEntity<List<UUID>> list(@PathVariable UUID agentId) {
        return RestControllerTool.of()
                .buildSuccessResponse(linkService.linkedKbIds(agentId, TenantContext.getCurrentTenant()));
    }
}
