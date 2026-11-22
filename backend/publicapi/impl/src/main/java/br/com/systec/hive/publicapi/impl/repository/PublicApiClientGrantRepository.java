package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PublicApiClientGrantRepository extends JpaRepository<PublicApiClientGrantEntity, UUID> {
    java.util.List<PublicApiClientGrantEntity> findByClientRef(java.util.UUID clientRef);

    void deleteAllByClientRef(java.util.UUID clientRef);
}
