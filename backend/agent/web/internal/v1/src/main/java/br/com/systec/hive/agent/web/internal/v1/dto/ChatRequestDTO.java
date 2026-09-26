package br.com.systec.hive.agent.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatRequestDTO(
        UUID agentId,
        UUID sessionId,
        @NotBlank @Size(max = 4000) String message
) {
}
