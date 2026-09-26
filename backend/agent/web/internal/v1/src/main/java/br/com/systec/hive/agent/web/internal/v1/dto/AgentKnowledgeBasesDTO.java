package br.com.systec.hive.agent.web.internal.v1.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PUT /v1/agents/{id}/knowledge-bases} — the full set of knowledge bases the agent
 * must be linked to after the call. An empty list unlinks everything.
 *
 * <p>{@code max = 5} mirrors RN-KB4 and {@code AgentKbLinkServiceImpl.MAX_LINKS}. It is declared here
 * as well as enforced in the service so the client gets a 400 naming the field instead of a generic
 * failure after the service already opened a transaction. The wording is the same sentence the
 * service throws, because the user sees both depending on which layer rejects first.
 */
public record AgentKnowledgeBasesDTO(
        @NotNull(message = "kbIds é obrigatório")
        @Size(max = 5, message = "Máximo de 5 bases de conhecimento por agente")
        @Schema(description = "Ids das bases de conhecimento; lista vazia desvincula todas")
        List<UUID> kbIds
) {
}
