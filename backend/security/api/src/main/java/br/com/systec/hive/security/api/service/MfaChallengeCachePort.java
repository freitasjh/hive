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

    /** Duração do bloqueio por usuário após estourar as tentativas (SPEC §5). */
    int BLOCK_TTL_SECONDS = 900;

    /** Salva o challenge com um nonce opaco de consumo gerado internamente. */
    default void save(MfaChallenge challenge) {
        save(challenge, UUID.randomUUID().toString());
    }

    /**
     * Salva o challenge e o nonce usado no compare-and-delete do sucesso.
     * O nonce nunca é devolvido ao cliente.
     */
    void save(MfaChallenge challenge, String consumeToken);

    Optional<MfaChallenge> get(UUID tenantId, UUID challengeId);

    /**
     * Incrementa atomicamente o contador de tentativas.
     *
     * @return número de tentativas após o incremento, ou {@code -1} se o challenge
     *         não existe mais (expirado/consumido) — o chamador trata como expirado.
     */
    int incrementAttempts(UUID tenantId, UUID challengeId);

    /**
     * Consome o challenge somente se o nonce ainda existir (compare-and-delete atômico).
     *
     * @return {@code true} apenas para o chamador que removeu o nonce — só ele pode emitir token.
     */
    boolean deleteIfTokenMatches(UUID tenantId, UUID challengeId, String expectedToken);

    void delete(UUID tenantId, UUID challengeId);

    /** Bloqueio por tenant+usuário por {@link #BLOCK_TTL_SECONDS} segundos. */
    void blockUser(UUID tenantId, UUID userId);

    boolean isUserBlocked(UUID tenantId, UUID userId);
}
