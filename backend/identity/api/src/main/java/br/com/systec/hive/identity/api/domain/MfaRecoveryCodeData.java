package br.com.systec.hive.identity.api.domain;

import java.time.Instant;
import java.util.UUID;

/** Representação de persistência de {@code user_mfa_recovery_code} (identity_db V4). */
public record MfaRecoveryCodeData(
        UUID id,
        UUID userId,
        String codeHash,
        Instant usedAt,
        Instant createdAt
) {
}
