package br.com.systec.hive.identity.impl.repository;

import br.com.systec.hive.identity.impl.entity.UserEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<UserEntity, UUID> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByUsernameOrEmail(String username, String email);

    Optional<UserEntity> findByDocument(String document);

    Optional<UserEntity> findByEmail(String email);

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET tenant_id = :tenantId WHERE id = :userId", nativeQuery = true)
    void updateTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
