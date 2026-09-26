package br.com.systec.hive.tenant.impl.service;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.tenant.impl.entity.TenantEntity;
import br.com.systec.hive.tenant.impl.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private TenantRepository repository;

    @InjectMocks
    private TenantServiceImpl service;

    @Test
    void when_create_then_success() {
        Tenant tenant = new Tenant();
        tenant.setName("Test Tenant");
        tenant.setOwnerId(UUID.randomUUID());

        TenantEntity tenantSaved = new TenantEntity();
        tenantSaved.setId(UUID.randomUUID());
        tenantSaved.setName(tenant.getName());
        tenantSaved.setOwnerId(tenant.getOwnerId());

        when(repository.save(any(TenantEntity.class))).thenReturn(tenantSaved);

        Tenant result = service.create(tenant);

        assertNotNull(result);
        assertEquals(tenantSaved.getId(), result.getId());
        assertEquals(tenantSaved.getName(), result.getName());
        assertEquals(tenantSaved.getOwnerId(), result.getOwnerId());
        verify(repository).save(any(TenantEntity.class));
    }
}
