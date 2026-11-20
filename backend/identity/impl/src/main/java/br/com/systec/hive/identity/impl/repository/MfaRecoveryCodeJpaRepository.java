package br.com.systec.hive.identity.impl.repository;

import br.com.systec.hive.identity.impl.entity.MfaRecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MfaRecoveryCodeJpaRepository extends JpaRepository<MfaRecoveryCodeEntity, UUID> {

    List<MfaRecoveryCodeEntity> findByUserId(UUID userId);
}
