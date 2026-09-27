package br.com.systec.hive.agent.web.internal.v1.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One assistant turn on the wire: what the model said, and nothing about how it found out.
 */
public record ChatResponseDTO(
        UUID sessionId,
        String role,
        String message,
        Instant timestamp
) {
}
