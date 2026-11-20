package br.com.systec.hive.identity.api.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Representação de persistência de {@code user_mfa}, própria do módulo identity.
 *
 * <p>identity/api não depende de security/api: a porta de repositório fala esta estrutura,
 * e o módulo security faz o mapeamento VO &harr; dado de persistência. Isso preserva a
 * fronteira verificada (security/impl &rarr; identity/api, nunca o contrário).
 */
public record MfaSecretData(
        UUID userId,
        UUID tenantId,
        String secretCiphertext,
        boolean enabled,
        Instant confirmedAt
) {
}
