package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.agent.api.domain.PromptStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PromptTemplateDTO(
        UUID id,
        String name,
        String code,
        String description,
        List<String> tags,
        String template,
        Set<String> params,
        InferenceParamsDTO inferenceParams,
        PromptStatus status,
        Integer version,
        Instant createAt,
        Instant updateAt
) {
}
