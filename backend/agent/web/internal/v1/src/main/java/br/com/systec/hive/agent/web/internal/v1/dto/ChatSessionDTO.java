package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * Sessão na wire. {@code provider}/{@code model} são a escolha EFETIVA da conversa:
 * {@code null} significa que ela ainda está no default do tenant. A UI precisa ler esses
 * campos para pintar o seletor ao reabrir uma conversa.
 */
public record ChatSessionDTO(
        UUID id,
        UUID userId,
        UUID promptTemplateId,
        String title,
        String summary,
        Instant createAt,
        Instant updateAt,
        AiProvider provider,
        String model
) {
}
