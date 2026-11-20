package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.MfaChallenge;

import java.util.Optional;
import java.util.UUID;

/**
 * Cache de desafios MFA no Redis, isolado por tenant. Implementação em security/impl
 * (padrão {@code TokenCacheService}). O challenge nunca contém segredo nem código TOTP.
 */
public interface MfaChallengeCachePort {

    /** TTL do desafio, em segundos (SPEC §4). */
    int TTL_SECONDS = 120;

    /** Número máximo de tentativas antes do bloqueio (SPEC §4). */
    int MAX_ATTEMPTS = 5;

    void save(MfaChallenge challenge);

    Optional<MfaChallenge> get(UUID tenantId, UUID challengeId);

    /**
     * Incrementa atomicamente o contador de tentativas.
     *
     * @return número de tentativas após o incremento, ou {@code -1} se o challenge
     *         não existe mais (expirado/consumido) — o chamador trata como expirado.
     */
    int incrementAttempts(UUID tenantId, UUID challengeId);

    void delete(UUID tenantId, UUID challengeId);
}
