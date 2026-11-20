package br.com.systec.hive.security.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code /disable}: senha do usuário, exigida antes de remover o MFA. */
public record MfaPasswordDTO(
        @NotBlank(message = "Senha é obrigatória")
        String password
) {
}
