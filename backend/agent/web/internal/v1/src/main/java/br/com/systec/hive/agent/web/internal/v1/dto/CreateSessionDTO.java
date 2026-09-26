package br.com.systec.hive.agent.web.internal.v1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSessionDTO(
        @NotNull UUID agentId,
        @Size(max = 50) String title
) {
}
