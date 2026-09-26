package br.com.systec.hive.agent.web.internal.v1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code PATCH /v1/agents/{id}} — the pre-existing route that {@code KnowledgeBaseDetail}
 * uses to add or remove one link at a time.
 *
 * <p>It carries the same RN-KB4 limit as {@link AgentKnowledgeBasesDTO} (the canonical
 * {@code PUT /knowledge-bases} route). The two must not drift: they set the same relation, so the
 * same rule and the same wording. Before this constraint the limit was only reachable through the
 * service, which made a sixth id fail with {@code KB_TOO_MANY_LINKS} here and
 * {@code VALIDATION_ERROR} on the PUT — the same user action answered two different ways depending
 * on which route the client happened to call.
 *
 * <p>The number is a literal because an annotation value cannot reference
 * {@code AgentKbLinkServiceImpl.MAX_LINKS}: this module must not depend on {@code knowledge/impl}.
 * The service check stays as the second barrier for callers that bypass validation.
 */
public record AgentKnowledgePatchDTO(
        @NotNull(message = "knowledgeBaseIds é obrigatório")
        @Size(max = 5, message = "Máximo de 5 bases de conhecimento por agente")
        List<UUID> knowledgeBaseIds
) {
}
