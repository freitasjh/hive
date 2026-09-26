package br.com.systec.hive.agent.api.service;

import java.util.UUID;

/**
 * Tenant-scoped throttle for validate/discovery endpoints.
 * Lives in api so the web layer programs against the port, never the impl class.
 */
public interface ValidateRateLimitPolicy {

    void check(UUID tenantId);
}
