package br.com.systec.hive.tenant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;

import java.time.Instant;
import java.util.UUID;

/**
 * Explicit projection rather than the serialized entity: the table holds a vendor and a model, and
 * anything else that ever gets added to it is not part of this contract until someone says so
 * (OWASP API3 — no sensitive fields in the response DTO).
 */
public record TenantLlmConfigDTO(
        UUID id,
        UUID tenantId,
        AiProvider provider,
        String model,
        Instant createAt,
        Instant updateAt
) {
}
