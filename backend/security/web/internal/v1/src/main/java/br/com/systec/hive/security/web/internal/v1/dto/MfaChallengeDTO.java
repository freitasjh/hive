package br.com.systec.hive.security.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo de {@code /verify}. Usado APÓS a senha correta, sem Bearer: o cliente deve chamar
 * com {@code omitAuthorization: true} (padrão de refresh/logout), porque o
 * {@code SecurityFilter} valida o Bearer antes do controller. O tenant vem do cabeçalho
 * {@code X-Tenant-ID}, nunca do corpo.
 */
public record MfaChallengeDTO(
        @NotNull(message = "Challenge é obrigatório")
        UUID challengeId,
        @NotBlank(message = "Código é obrigatório")
        String code
) {
}
