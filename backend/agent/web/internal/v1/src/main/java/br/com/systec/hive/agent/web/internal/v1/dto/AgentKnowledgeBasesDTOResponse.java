package br.com.systec.hive.agent.web.internal.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Result of {@code PUT /v1/agents/{id}/knowledge-bases}.
 *
 * <p>Names are echoed alongside the ids so the editor can show the current selection without a
 * second request. Ids stay the source of truth: a name can be duplicated across a tenant.
 */
public record AgentKnowledgeBasesDTOResponse(
        @Schema(description = "Agente cujos vínculos foram substituídos") UUID agentId,
        @Schema(description = "Ids vinculados após a chamada") List<UUID> kbIds,
        @Schema(description = "Nomes correspondentes, na mesma ordem de kbIds") List<String> kbNames
) {
}
