package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.ProviderCredential;
import br.com.systec.hive.agent.api.service.ProviderCredentialService;
import br.com.systec.hive.agent.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.agent.impl.mapper.ProviderCredentialMapperDomain;
import br.com.systec.hive.agent.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.agent.impl.security.CredentialPolicy;
import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ProviderCredentialServiceImpl implements ProviderCredentialService {

    private static final Logger log = LoggerFactory.getLogger(ProviderCredentialServiceImpl.class);

    private final ProviderCredentialRepository repository;
    private final ProviderCredentialMapperDomain mapper;

    public ProviderCredentialServiceImpl(ProviderCredentialRepository repository,
                                         ProviderCredentialMapperDomain mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProviderCredential upsert(UUID tenantId, AiProvider provider, String apiKey, String baseUrl) {
        CredentialPolicy.validateUpsert(provider, apiKey, baseUrl);
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim();
        if (VendorCatalog.baseUrlFor(provider) != null) {
            // GO/Zen: o eco tolerado é a própria constante do vendor — nunca vira estado do tenant.
            normalizedBaseUrl = null;
        }
        ProviderCredentialEntity entity = repository.findByTenantIdAndProvider(tenantId, provider)
                .orElseGet(() -> {
                    ProviderCredentialEntity created = new ProviderCredentialEntity();
                    created.setTenantId(tenantId);
                    created.setProvider(provider);
                    return created;
                });
        if (provider != AiProvider.OLLAMA) {
            String newKey = apiKey.trim();
            bumpKeyVersionOnRotation(entity, newKey);
            entity.setApiKey(newKey);
        } else {
            entity.setApiKey(null);
        }
        entity.setBaseUrl(normalizedBaseUrl);
        ProviderCredentialEntity saved = repository.save(entity);
        log.info("credential upsert tenantId={} provider={}", tenantId, provider);
        return toStatus(saved, provider == AiProvider.OLLAMA ? null : apiKey.trim());
    }

    private void bumpKeyVersionOnRotation(ProviderCredentialEntity entity, String newKey) {
        if (entity.getId() == null) {
            entity.setKeyVersion(1);
            return;
        }
        String currentKey = entity.getApiKey();
        if (currentKey == null || !currentKey.equals(newKey)) {
            entity.setKeyVersion(entity.getKeyVersion() + 1);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderCredential> listStatuses(UUID tenantId) {
        List<ProviderCredentialEntity> stored = repository.findByTenantId(tenantId);
        return java.util.Arrays.stream(AiProvider.values())
                .map(provider -> stored.stream()
                        .filter(e -> e.getProvider() == provider)
                        .findFirst()
                        .map(entity -> toStatus(entity, entity.getApiKey()))
                        .orElseGet(() -> unconfigured(provider)))
                .sorted(Comparator.comparing(c -> c.getProvider().name()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, AiProvider provider) {
        ProviderCredentialEntity entity = repository.findByTenantIdAndProvider(tenantId, provider)
                .orElseThrow(() -> new ObjectNotFoundException("Credencial não encontrada."));
        repository.delete(entity);
        log.info("credential delete tenantId={} provider={}", tenantId, provider);
    }

    private ProviderCredential toStatus(ProviderCredentialEntity entity, String plainKey) {
        ProviderCredential status = mapper.toDomain(entity);
        status.setConfigured(true);
        status.setLast4(last4(plainKey));
        return status;
    }

    private ProviderCredential unconfigured(AiProvider provider) {
        ProviderCredential status = new ProviderCredential();
        status.setProvider(provider);
        status.setConfigured(false);
        status.setLast4(null);
        status.setBaseUrl(null);
        status.setUpdatedAt(null);
        return status;
    }

    static String last4(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) {
            return null;
        }
        return apiKey.substring(apiKey.length() - 4);
    }
}
