package br.com.systec.hive.assistant.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSessionDTO(
        @NotBlank @Size(max = 50) String title
) {
}
