package br.com.systec.hive.identity.impl.repository;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.identity.impl.entity.MfaRecoveryCodeEntity;
import br.com.systec.hive.identity.impl.entity.MfaSecretEntity;
import br.com.systec.hive.identity.impl.mapper.MfaPersistenceConverter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA da porta {@link MfaRepositoryPort}. Fica em identity/impl, ao lado de
 * {@code UserRepository}. Escritas exigem transação (REQUIRED); leitura é SUPPORTS.
 */
@Repository
public class MfaRepositoryAdapter implements MfaRepositoryPort {

    private final MfaSecretJpaRepository secretRepository;
    private final MfaRecoveryCodeJpaRepository recoveryCodeRepository;

    public MfaRepositoryAdapter(MfaSecretJpaRepository secretRepository,
                                MfaRecoveryCodeJpaRepository recoveryCodeRepository) {
        this.secretRepository = secretRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public MfaSecretData save(MfaSecretData secret) {
        MfaSecretEntity entity = secretRepository.save(MfaPersistenceConverter.of(secret));
        return MfaPersistenceConverter.of(entity);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<MfaSecretData> findByUserId(UUID userId) {
        return secretRepository.findByUserId(userId).map(MfaPersistenceConverter::of);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<MfaSecretData> findByUserIdAndTenantId(UUID userId, UUID tenantId) {
        return secretRepository.findByUserIdAndTenantId(userId, tenantId).map(MfaPersistenceConverter::of);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean existsByUserId(UUID userId) {
        return secretRepository.existsById(userId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteByUserId(UUID userId) {
        // Cascade ON DELETE em user_mfa_recovery_code remove os códigos junto (V4).
        secretRepository.deleteById(userId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public MfaRecoveryCodeData saveRecoveryCode(MfaRecoveryCodeData recoveryCode) {
        MfaRecoveryCodeEntity entity = recoveryCodeRepository.save(MfaPersistenceConverter.of(recoveryCode));
        return MfaPersistenceConverter.of(entity);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<MfaRecoveryCodeData> findRecoveryCodesByUserId(UUID userId) {
        return recoveryCodeRepository.findByUserId(userId).stream()
                .map(MfaPersistenceConverter::of)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<MfaRecoveryCodeData> findRecoveryCodeById(UUID id) {
        return recoveryCodeRepository.findById(id).map(MfaPersistenceConverter::of);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void markRecoveryCodeUsed(UUID id, Instant usedAt) {
        recoveryCodeRepository.findById(id).ifPresent(entity -> {
            entity.setUsedAt(usedAt);
            recoveryCodeRepository.save(entity);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int markRecoveryCodeUsedIfUnused(UUID id, Instant usedAt) {
        // UPDATE atômico no banco (@Modifying/@Query na interface Spring Data).
        // A condição `usedAt is null` garante uso único mesmo sob concorrência.
        return recoveryCodeRepository.markRecoveryCodeUsedIfUnused(id, usedAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void deleteRecoveryCodesByUserId(UUID userId) {
        recoveryCodeRepository.deleteAll(recoveryCodeRepository.findByUserId(userId));
    }
}
