package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PublicApiClientGrantRepository extends JpaRepository<PublicApiClientGrantEntity, UUID> {
    List<PublicApiClientGrantEntity> findByClientRef(java.util.UUID clientRef);

    /** Bugfix N+1: uma query para todos os clients da página. */
    List<PublicApiClientGrantEntity> findByClientRefIn(java.util.Collection<java.util.UUID> clientRefs);

    void deleteAllByClientRef(java.util.UUID clientRef);
}
