package br.com.systec.hive.security.web.internal.v1.dto;

/**
 * Erro semântico MFA: apenas nome do código e mensagem fixa. Nunca contém segredo,
 * código TOTP, recovery code, senha ou token.
 */
public record MfaErrorResponseDTO(
        String code,
        String message
) {
}
