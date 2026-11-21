package br.com.systec.hive.assistant.impl.repository;

import br.com.systec.hive.assistant.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.provider.api.AiProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCredentialRepository extends JpaRepository<ProviderCredentialEntity, UUID> {

    Optional<ProviderCredentialEntity> findByTenantIdAndProvider(UUID tenantId, AiProvider provider);

    List<ProviderCredentialEntity> findByTenantId(UUID tenantId);

    /**
     * Lê o ciphertext bruto da credencial mais antiga, sem materializar a entidade (portanto sem
     * disparar o {@code AesGcmAttributeConverter}) e sem o filtro de tenant, para que a
     * checagem de startup seja global.
     *
     * <p>Uso restrito à verificação de consistência da {@code PROVIDER_MASTER_KEY}: o valor
     * decifrado nunca sai do chamador e nunca é logado.
     */
    @Query(value = "SELECT ciphertext FROM provider_credential "
            + "WHERE ciphertext IS NOT NULL ORDER BY create_at LIMIT 1", nativeQuery = true)
    Optional<String> findFirstRawCiphertext();
}
