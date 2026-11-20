package br.com.systec.hive.security.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Desafio MFA emitido após a senha correta, mantido no Redis com TTL curto.
 *
 * <p>Vinculado a {@code tenantId}, {@code remoteIp} e {@code userAgent} para resistir a
 * sequestro de sessão (mesma robustez do refresh token). <strong>Nunca</strong> carrega
 * segredo nem código TOTP — apenas metadados e o contador de tentativas.
 */
public record MfaChallenge(
        UUID id,
        UUID userId,
        UUID tenantId,
        String remoteIp,
        String userAgent,
        int attempts,
        Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
