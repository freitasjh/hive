package br.com.systec.hive.tenant.impl.mapper;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.tenant.impl.entity.TenantEntity;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TenantMapperTest {

    private final TenantMapper mapper = TenantMapper.INSTANCE;

    @Test
    void when_toEntity_then_returnEntity() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test Tenant");
        tenant.setOwnerId(UUID.randomUUID());

        TenantEntity result = mapper.toEntity(tenant);

        assertNotNull(result);
        assertEquals(tenant.getId(), result.getId());
        assertEquals(tenant.getName(), result.getName());
        assertEquals(tenant.getOwnerId(), result.getOwnerId());
    }

    @Test
    void when_toDomain_then_returnDomain() {
        TenantEntity entity = new TenantEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Test Tenant");
        entity.setOwnerId(UUID.randomUUID());

        Tenant result = mapper.toDomain(entity);

        assertNotNull(result);
        assertEquals(entity.getId(), result.getId());
        assertEquals(entity.getName(), result.getName());
        assertEquals(entity.getOwnerId(), result.getOwnerId());
    }
}
