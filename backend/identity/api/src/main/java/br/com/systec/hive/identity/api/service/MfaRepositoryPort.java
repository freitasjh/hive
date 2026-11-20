package br.com.systec.hive.identity.api.service;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência do MFA, implementada em identity/impl (JPA) e consumida por
 * security/impl. Tipos próprios de identity evitam dependência de security/api.
 */
public interface MfaRepositoryPort {

    MfaSecretData save(MfaSecretData secret);

    Optional<MfaSecretData> findByUserId(UUID userId);

    Optional<MfaSecretData> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    MfaRecoveryCodeData saveRecoveryCode(MfaRecoveryCodeData recoveryCode);

    List<MfaRecoveryCodeData> findRecoveryCodesByUserId(UUID userId);

    Optional<MfaRecoveryCodeData> findRecoveryCodeById(UUID id);

    void markRecoveryCodeUsed(UUID id, Instant usedAt);

    void deleteRecoveryCodesByUserId(UUID userId);
}
