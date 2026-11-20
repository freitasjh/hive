package br.com.systec.hive.identity.impl.repository;

import br.com.systec.hive.identity.impl.entity.MfaRecoveryCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MfaRecoveryCodeJpaRepository extends JpaRepository<MfaRecoveryCodeEntity, UUID> {

    List<MfaRecoveryCodeEntity> findByUserId(UUID userId);

    /**
     * UPDATE condicional: a cláusula {@code usedAt is null} é avaliada pelo banco no
     * momento da escrita. Duas transações concorrentes disputam a mesma linha; apenas
     * a que executar o UPDATE primeiro obtém 1 linha afetada. A outra obtém 0.
     */
    @Modifying
    @Query("update MfaRecoveryCodeEntity c set c.usedAt = :usedAt " +
            "where c.id = :id and c.usedAt is null")
    int markRecoveryCodeUsedIfUnused(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
