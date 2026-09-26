package br.com.systec.hive.commons.impl.security;

import br.com.systec.hive.commons.api.exceptions.TenantNotFountException;

import java.lang.ScopedValue;
import java.util.UUID;

public class TenantContext {
    private static final ScopedValue<UUID> CURRENT_TENANT = ScopedValue.newInstance();

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.orElseThrow(TenantNotFountException::new);
    }

    public static <R> R runWithTenant(UUID tenantId, java.util.concurrent.Callable<R> task) throws Exception {
        return ScopedValue.where(CURRENT_TENANT, tenantId).call(task::call);
    }
}
