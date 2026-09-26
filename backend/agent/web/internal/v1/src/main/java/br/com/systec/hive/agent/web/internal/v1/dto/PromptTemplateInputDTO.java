package br.com.systec.hive.agent.web.internal.v1.dto;

import java.util.Set;

public record PromptTemplateInputDTO(
    String code,
    String name,
    String description,
    Set<String> params
) {}
