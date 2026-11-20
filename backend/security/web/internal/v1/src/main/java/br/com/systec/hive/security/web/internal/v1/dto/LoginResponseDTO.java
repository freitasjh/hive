package br.com.systec.hive.security.web.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Resposta de login com token: {userId, accessToken, type, mfaSetupAvailable}.
 *
 * <p>{@code mfaSetupAvailable} é um convite opcional de onboarding MFA — booleano derivado
 * no login, sem qualquer segredo ou estado detalhado. O construtor de 3 argumentos mantém
 * o contrato de verify/recovery inalterado: o campo fica {@code null} e é omitido do JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponseDTO(
    UUID userId,
    String accessToken,
    String type,
    Boolean mfaSetupAvailable
    ) {
    public LoginResponseDTO(UUID userId, String accessToken, String type) {
        this(userId, accessToken, type, null);
    }
}