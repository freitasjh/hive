package br.com.systec.hive.security.web.internal.v1.dto;

import java.util.List;

/**
 * Resposta única do setup: secret em claro, QR em data URI e os 8 recovery codes.
 *
 * <p>Segredo e códigos são exibidos <strong>uma única vez</strong> e nunca logados. Em repouso
 * só existem o ciphertext do secret e o hash dos códigos.
 */
public record MfaSetupResponseDTO(
        String secret,
        String qrDataUri,
        List<String> recoveryCodes
) {
}
