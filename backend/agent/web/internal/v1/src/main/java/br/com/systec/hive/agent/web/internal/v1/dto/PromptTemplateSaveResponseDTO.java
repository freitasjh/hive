package br.com.systec.hive.agent.web.internal.v1.dto;

import java.util.UUID;

public record PromptTemplateSaveResponseDTO(
    UUID id,
    String code
) {}
