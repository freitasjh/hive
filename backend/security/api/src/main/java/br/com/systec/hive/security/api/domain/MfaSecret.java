package br.com.systec.hive.security.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Segredo TOTP de um usuário, já cifrado em repouso (AES-256-GCM).
 *
 * <p><strong>Nunca</strong> contém o segredo em claro: {@code secretCiphertext} é o envelope
 * cifrado gravado em {@code user_mfa.secret_ciphertext}. O segredo em claro só existe no
 * instante do setup (Fase 2) e não é persistido nem logado em nenhum caminho.
 */
public record MfaSecret(
        UUID userId,
        UUID tenantId,
        String secretCiphertext,
        boolean enabled,
        Instant confirmedAt
) {
}
