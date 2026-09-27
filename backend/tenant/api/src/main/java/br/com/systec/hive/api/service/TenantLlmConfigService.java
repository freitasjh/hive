package br.com.systec.hive.api.service;

import br.com.systec.hive.api.domain.TenantLlmConfig;

import java.util.UUID;

/**
 * The tenant's single default LLM. One row per tenant, no fallback, no per-user variant.
 *
 * <p>{@link #require(UUID)} is the method the chat path uses and its name is the contract: it either
 * returns a usable provider and model or throws. There is deliberately no {@code findOrDefault}
 * anywhere on this interface — an Optional-returning read is how a global fallback gets introduced
 * later by someone who never intended one.
 */
public interface TenantLlmConfigService {

    TenantLlmConfig get(UUID tenantId);

    /**
     * @throws br.com.systec.hive.api.exceptions.TenantLlmConfigNotFoundException when the tenant has
     *         no configuration. There is no other outcome.
     */
    TenantLlmConfig require(UUID tenantId);

    /**
     * Creates or replaces the tenant's single configuration.
     *
     * @param provider must be on {@link br.com.systec.hive.api.domain.TenantLlmConfigProviders}
     * @param model    must not be blank
     */
    TenantLlmConfig upsert(UUID tenantId, TenantLlmConfig config);
}
