package br.com.systec.hive.security.web.internal.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corpo de {@code /recovery}. Mesma regra de {@link MfaChallengeDTO}: sem Bearer
 * ({@code omitAuthorization}), tenant via {@code X-Tenant-ID} e desafio vinculado ao challenge.
 * O código de recuperação é de uso único e trafega apenas neste corpo — nunca em URL ou log.
 */
public record MfaRecoveryDTO(
        @NotNull(message = "Challenge é obrigatório")
        UUID challengeId,
        @NotBlank(message = "Código de recuperação é obrigatório")
        String recoveryCode
) {
}
