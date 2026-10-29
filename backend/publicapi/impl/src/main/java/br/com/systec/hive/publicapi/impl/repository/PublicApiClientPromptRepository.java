package br.com.systec.hive.publicapi.impl.repository;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientPromptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PublicApiClientPromptRepository extends JpaRepository<PublicApiClientPromptEntity, UUID> {
    java.util.List<PublicApiClientPromptEntity> findByClientRef(java.util.UUID clientRef);
}
