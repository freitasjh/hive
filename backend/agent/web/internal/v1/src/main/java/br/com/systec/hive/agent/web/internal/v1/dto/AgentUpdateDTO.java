package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AgentUpdateDTO(
        @NotBlank(message = "name must not be blank")
        @Size(max = 200, message = "name must be <= 200")
        String name,

        @Pattern(regexp = "^https?://.*", message = "avatarUrl must be a valid URL starting with http:// or https://")
        @Size(max = 2048, message = "avatarUrl must be <= 2048")
        String avatarUrl,

        @Size(max = 2000, message = "description must be <= 2000")
        String description,

        @Size(max = 20000, message = "systemPrompt must be <= 20000")
        String systemPrompt,

        UUID promptTemplateId,

        @NotBlank(message = "model must not be blank")
        @Size(max = 200, message = "model must be <= 200")
        String model,

        @NotNull(message = "provider must not be null")
        AiProvider provider,

        @DecimalMin(value = "0.0", message = "temperature must be >= 0.0")
        @DecimalMax(value = "2.0", message = "temperature must be <= 2.0")
        Double temperature,

        @DecimalMin(value = "0.0", message = "topP must be >= 0.0")
        @DecimalMax(value = "1.0", message = "topP must be <= 1.0")
        Double topP,

        @Min(value = 256, message = "maxTokens must be >= 256")
        @Max(value = 4096, message = "maxTokens must be <= 4096")
        Integer maxTokens,

        @Size(max = 20, message = "tools must have at most 20 items")
        List<@NotBlank @Size(max = 100) String> tools,

        @Size(max = 20, message = "knowledgeBases must have at most 20 items")
        List<@NotBlank @Size(max = 100) String> knowledgeBases,

        Boolean isDefault,

        Boolean active
) {
}
