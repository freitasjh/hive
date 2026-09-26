package br.com.systec.hive.agent.web.internal.v1.dto;

import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Set;

public record PromptTemplateInfoDTO(
    UUID id,
    String code,
    String name,
    String description,
    Set<String> params,
    boolean active,
    boolean draft,
    LocalDateTime createAt
) {}
