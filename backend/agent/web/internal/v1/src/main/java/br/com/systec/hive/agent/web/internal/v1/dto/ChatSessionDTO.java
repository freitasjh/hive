package br.com.systec.hive.agent.web.internal.v1.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSessionDTO(
        UUID id,
        UUID userId,
        UUID promptTemplateId,
        String title,
        String summary,
        Instant createAt,
        Instant updateAt
) {
}
