package br.com.systec.hive.assistant.web.internal.v1.dto;

import br.com.systec.hive.assistant.api.domain.PromptStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PromptTemplateUpdateDTO(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must be <= 200")
        String name,

        @Size(max = 2000, message = "description must be <= 2000")
        String description,

        @Size(max = 20, message = "tags must have at most 20 items")
        List<@NotBlank @Size(max = 50) String> tags,

        @NotBlank(message = "template must not be blank")
        @Size(max = 20000, message = "template must be <= 20000")
        String template,

        @Valid
        InferenceParamsDTO inferenceParams,

        PromptStatus status
) {
}
