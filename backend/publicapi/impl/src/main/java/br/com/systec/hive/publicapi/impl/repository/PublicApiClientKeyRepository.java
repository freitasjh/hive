package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PublicApiClientKeyRepository extends JpaRepository<PublicApiClientKeyEntity, UUID> {

    Optional<PublicApiClientKeyEntity> findByKeyId(String keyId);

    /** Uma query indexada cobre a key atual e a anterior (janela de rotação de 10 min). */
    Optional<PublicApiClientKeyEntity> findByKeyIdOrPreviousKeyId(String keyId, String previousKeyId);

    List<PublicApiClientKeyEntity> findByClientRef(UUID clientRef);

    List<PublicApiClientKeyEntity> findByTenantId(UUID tenantId);
}
