package br.com.systec.hive.tenant.impl.service;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.api.service.TenantService;
import br.com.systec.hive.tenant.impl.entity.TenantEntity;
import br.com.systec.hive.tenant.impl.mapper.TenantMapper;
import br.com.systec.hive.tenant.impl.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.OperationNotSupportedException;

@Service
public class TenantServiceImpl implements TenantService {

    private final TenantRepository repository;

    public TenantServiceImpl(TenantRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Tenant create(Tenant tenant) {
        TenantEntity tenantToSave = TenantMapper.INSTANCE.toEntity(tenant);

        TenantEntity tenantSaved = repository.save(tenantToSave);

        return TenantMapper.INSTANCE.toDomain(tenantSaved);
    }

    @Override
    @Transactional
    public Tenant update(Tenant tenant) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public Tenant delete(Tenant tenant) {
        return null;
    }
}
