package br.com.systec.hive.security.web.internal.v1.dto;

import java.util.UUID;

/**
 * Resposta do login quando o usuário possui MFA habilitado (SPEC §4).
 *
 * <p><strong>Nunca</strong> contém access token nem cookie de refresh — o cliente deve
 * submeter o código em {@code /v1/auth/mfa/verify} com este {@code challengeId}.
 */
public record MfaChallengeResponseDTO(
        boolean mfaRequired,
        UUID challengeId,
        int expiresIn
) {
}
