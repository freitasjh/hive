package br.com.systec.hive.tenant.impl.service;

import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.domain.TenantLlmConfigProviders;
import br.com.systec.hive.api.exceptions.ConcurrentLlmConfigUpdateException;
import br.com.systec.hive.api.exceptions.ProviderNotAllowedException;
import br.com.systec.hive.api.exceptions.TenantLlmConfigNotFoundException;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.tenant.impl.entity.TenantLlmConfigEntity;
import br.com.systec.hive.tenant.impl.mapper.TenantLlmConfigMapperDomain;
import br.com.systec.hive.tenant.impl.repository.TenantLlmConfigRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Validation lives here, not only in the DTO and not only in the {@code CHECK} constraint, because
 * the three are read by three different callers and the service is the only one all of them pass
 * through.
 */
@Service
public class TenantLlmConfigServiceImpl implements TenantLlmConfigService {

    private final TenantLlmConfigRepository repository;
    private final TenantLlmConfigMapperDomain mapper;

    public TenantLlmConfigServiceImpl(TenantLlmConfigRepository repository,
                                      TenantLlmConfigMapperDomain mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantLlmConfig get(UUID tenantId) {
        return repository.findByTenantId(tenantId).map(mapper::toDomain).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantLlmConfig require(UUID tenantId) {
        return repository.findByTenantId(tenantId)
                .map(mapper::toDomain)
                .orElseThrow(TenantLlmConfigNotFoundException::new);
    }

    @Override
    @Transactional
    public TenantLlmConfig upsert(UUID tenantId, TenantLlmConfig config) {
        validate(config);

        TenantLlmConfigEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(TenantLlmConfigEntity::new);
        entity.setProvider(config.getProvider());
        entity.setModel(config.getModel().trim());
        // The row is the tenant's, so its identity is the tenant. Set on create only; on update
        // AbstractEntity.tenantId is updatable = false and setting it would be a no-op anyway.
        if (entity.getTenantId() == null) {
            entity.setTenantId(tenantId);
        }

        try {
            TenantLlmConfigEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // The UNIQUE on tenant_id is the real serializer: two concurrent upserts for the same
            // tenant would both read "absent" and both insert. Letting the constraint speak is
            // better than read-modify-write locking we would have to remember to take.
            throw new ConcurrentLlmConfigUpdateException(e);
        }
    }

    private static void validate(TenantLlmConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config é obrigatório");
        }
        if (!TenantLlmConfigProviders.isAllowed(config.getProvider())) {
            throw new ProviderNotAllowedException(String.valueOf(config.getProvider()));
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new IllegalArgumentException("model não pode ser vazio");
        }
    }
}
