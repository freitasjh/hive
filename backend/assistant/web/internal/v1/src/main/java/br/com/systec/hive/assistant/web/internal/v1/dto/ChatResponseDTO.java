package br.com.systec.hive.assistant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * One assistant turn on the wire: what the model said, and nothing about how it found out.
 * {@code provider}/{@code model} are the EFFECTIVE pair that answered the turn, so the client
 * can show them without re-deriving tenant defaults.
 */
public record ChatResponseDTO(
        UUID sessionId,
        String role,
        String message,
        Instant timestamp,
        AiProvider provider,
        String model
) {
}
