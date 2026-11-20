package br.com.systec.hive.security.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code /enable}: código TOTP de 6 dígitos confirmando o setup. */
public record MfaCodeDTO(
        @NotBlank(message = "Código é obrigatório")
        @Size(max = 64, message = "Código inválido")
        String code
) {
}
