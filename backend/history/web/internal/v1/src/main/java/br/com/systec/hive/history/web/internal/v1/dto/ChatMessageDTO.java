package br.com.systec.hive.history.web.internal.v1.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageDTO(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        Instant createAt
) {
}
