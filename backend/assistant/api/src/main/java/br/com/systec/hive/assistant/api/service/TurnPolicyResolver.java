package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.provider.api.AiProvider;

import java.util.UUID;

/**
 * ADR-005: porta da política de provedor/modelo de um turno. A implementação permanece em
 * {@code assistant/impl} — a política é dona da API, a infra de credencial não.
 */
public interface TurnPolicyResolver {

    Resolved resolve(UUID tenantId);

    Resolved resolve(UUID tenantId, AiProvider providerOverride, String modelOverride);

    /** Par efetivo que respondeu o turno; sem segredos além da chave já resolvida para a chamada. */
    record Resolved(AiProvider provider, String model, String apiKey, String baseUrl) {
    }
}
