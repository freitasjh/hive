package br.com.systec.hive.assistant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ValidateRequestDTO(
        @NotNull(message = "provider must not be null")
        AiProvider provider,

        @NotBlank(message = "model must not be blank")
        @Size(max = 200, message = "model must be <= 200")
        String model,

        @Size(max = 2048, message = "baseUrl must be <= 2048")
        String baseUrl
) {
}
