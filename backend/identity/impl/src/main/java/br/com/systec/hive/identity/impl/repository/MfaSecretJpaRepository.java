package br.com.systec.hive.identity.impl.repository;

import br.com.systec.hive.identity.impl.entity.MfaSecretEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaSecretJpaRepository extends JpaRepository<MfaSecretEntity, UUID> {

    Optional<MfaSecretEntity> findByUserId(UUID userId);

    Optional<MfaSecretEntity> findByUserIdAndTenantId(UUID userId, UUID tenantId);
}
