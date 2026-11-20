package br.com.systec.hive.security.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Código de recuperação de MFA. Persistido apenas como hash SHA-256 ({@code codeHash});
 * o código em claro é exibido uma única vez no setup e nunca gravado.
 */
public record MfaRecoveryCode(
        UUID id,
        UUID userId,
        String codeHash,
        Instant usedAt
) {
    public boolean isUsed() {
        return usedAt != null;
    }
}
