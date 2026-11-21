package br.com.systec.hive.assistant.web.internal.v1.dto;

import java.util.UUID;

public record PromptTemplateSaveResponseDTO(
    UUID id,
    String code
) {}
