package br.com.systec.hive.publicapi.impl.provisioning;

import br.com.systec.hive.publicapi.impl.entity.PublicApiClientGrantEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientPromptEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientGrantRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientPromptRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * ADR-003 (Decisão 2c): única regra de escrita de grants e vínculos de prompt.
 * Chamada tanto pela CLI ({@link PublicApiProvisioningRunner}) quanto pela API admin — não duplicar.
 */
@Component
public class PublicApiClientAccessWriter {

    /** Grant padrão de um client público; o mesmo da CLI histórica. */
    public static final String PUBLIC_CHAT_GRANT = "PUBLIC_CHAT_COMPLETE";
    public static final String PIN_FOLLOW_ACTIVE = "FOLLOW_ACTIVE";
    public static final String PINNED = "PINNED";

    /** Par pin/versão sem DTO de framework, para ser usado por CLI e API. */
    public record PromptBinding(String promptCode, String pinMode, Integer pinnedVersion) {
    }

    private final PublicApiClientGrantRepository grantRepository;
    private final PublicApiClientPromptRepository promptRepository;

    public PublicApiClientAccessWriter(PublicApiClientGrantRepository grantRepository,
                                       PublicApiClientPromptRepository promptRepository) {
        this.grantRepository = grantRepository;
        this.promptRepository = promptRepository;
    }

    /** Bugfix grants: validação no ponto de escrita; catálogo fechado, sem silêncio para string inventada. */
    public static void validateGrants(List<String> grants) {
        if (grants == null) return;
        for (String permission : grants) {
            if (permission == null || permission.isBlank()) {
                throw new IllegalArgumentException("permission não pode ser vazia");
            }
            if (!PUBLIC_CHAT_GRANT.equals(permission)) {
                throw new IllegalArgumentException("permission desconhecida: " + permission
                        + ". Catálogo: " + PUBLIC_CHAT_GRANT);
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void write(UUID tenantId, UUID clientRef, List<String> grants, List<PromptBinding> prompts) {
        validateGrants(grants);
        if (grants != null) {
            for (String permission : new LinkedHashSet<>(grants)) {
                if (permission == null || permission.isBlank()) {
                    continue;
                }
                PublicApiClientGrantEntity grant = new PublicApiClientGrantEntity();
                grant.setTenantId(tenantId);
                grant.setClientRef(clientRef);
                grant.setPermission(permission);
                grantRepository.save(grant);
            }
        }
        if (prompts != null) {
            for (PromptBinding binding : prompts) {
                if (binding == null || binding.promptCode() == null || binding.promptCode().isBlank()) {
                    continue;
                }
                String pinMode = binding.pinMode() == null || binding.pinMode().isBlank()
                        ? PIN_FOLLOW_ACTIVE : binding.pinMode();
                if (!PIN_FOLLOW_ACTIVE.equals(pinMode) && !PINNED.equals(pinMode)) {
                    throw new IllegalArgumentException("pin_mode inválido: " + pinMode);
                }
                if (PINNED.equals(pinMode) && (binding.pinnedVersion() == null || binding.pinnedVersion() < 1)) {
                    throw new IllegalArgumentException("PINNED exige pinned_version >= 1");
                }
                if (PIN_FOLLOW_ACTIVE.equals(pinMode) && binding.pinnedVersion() != null) {
                    throw new IllegalArgumentException("FOLLOW_ACTIVE não aceita pinned_version");
                }
                PublicApiClientPromptEntity prompt = new PublicApiClientPromptEntity();
                prompt.setTenantId(tenantId);
                prompt.setClientRef(clientRef);
                prompt.setPromptCode(binding.promptCode());
                prompt.setPinMode(pinMode);
                prompt.setPinnedVersion(binding.pinnedVersion());
                promptRepository.save(prompt);
            }
        }
    }
}
