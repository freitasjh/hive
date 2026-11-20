package br.com.systec.hive.security.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Desafio MFA emitido após a senha correta, mantido no Redis com TTL curto.
 *
 * <p>Vinculado a {@code tenantId}, {@code remoteIp} e {@code userAgent} para resistir a
 * sequestro de sessão (mesma robustez do refresh token). <strong>Nunca</strong> carrega
 * segredo nem código TOTP — apenas metadados e o contador de tentativas.
 *
 * <p>{@code consumeToken} é um nonce opaco usado no compare-and-delete do consumo: dois
 * verify concorrentes leem o mesmo challenge, mas só quem remover o nonce em Redis emite token.
 * {@code jti}, quando presente, identifica um access token anterior que deve ser blacklistado
 * no sucesso do segundo fator.
 */
public record MfaChallenge(
        UUID id,
        UUID userId,
        UUID tenantId,
        String remoteIp,
        String userAgent,
        int attempts,
        Instant expiresAt,
        String consumeToken,
        String jti
) {
    /** Compatibilidade com challenges sem nonce/JTI (Fase 2). */
    public MfaChallenge(UUID id, UUID userId, UUID tenantId, String remoteIp, String userAgent,
                        int attempts, Instant expiresAt) {
        this(id, userId, tenantId, remoteIp, userAgent, attempts, expiresAt, null, null);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
