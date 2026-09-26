package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;

public record ValidateResponseDTO(
        boolean ok,
        AiProvider provider,
        String model,
        long latencyMs,
        String reason
) {
}
