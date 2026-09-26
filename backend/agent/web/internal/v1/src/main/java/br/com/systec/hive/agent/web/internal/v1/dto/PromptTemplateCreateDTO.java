package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PromptTemplateCreateDTO(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must be <= 200")
        String name,

        @NotBlank(message = "code must not be blank")
        @Size(max = 100, message = "code must be <= 100")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "code must match ^[a-zA-Z0-9_-]+$")
        String code,

        @Size(max = 2000, message = "description must be <= 2000")
        String description,

        @Size(max = 20, message = "tags must have at most 20 items")
        List<@NotBlank @Size(max = 50, message = "tag must be <= 50") String> tags,

        @NotBlank(message = "template must not be blank")
        @Size(max = 20000, message = "template must be <= 20000")
        String template,

        @Valid
        InferenceParamsDTO inferenceParams,

        PromptStatus status
) {
}
