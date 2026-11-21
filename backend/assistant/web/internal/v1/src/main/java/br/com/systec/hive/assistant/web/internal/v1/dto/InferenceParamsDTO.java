package br.com.systec.hive.assistant.web.internal.v1.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record InferenceParamsDTO(
        @DecimalMin(value = "0.0", message = "temperature must be >= 0.0")
        @DecimalMax(value = "2.0", message = "temperature must be <= 2.0")
        Double temperature,

        @DecimalMin(value = "0.0", message = "topP must be >= 0.0")
        @DecimalMax(value = "1.0", message = "topP must be <= 1.0")
        Double topP,

        @Min(value = 256, message = "maxTokens must be >= 256")
        @Max(value = 4096, message = "maxTokens must be <= 4096")
        Integer maxTokens
) {
}
