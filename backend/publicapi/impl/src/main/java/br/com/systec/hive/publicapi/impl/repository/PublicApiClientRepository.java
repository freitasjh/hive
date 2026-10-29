package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PublicApiClientRepository extends JpaRepository<PublicApiClientEntity, UUID> {
    java.util.Optional<PublicApiClientEntity> findByClientId(String clientId);
}
