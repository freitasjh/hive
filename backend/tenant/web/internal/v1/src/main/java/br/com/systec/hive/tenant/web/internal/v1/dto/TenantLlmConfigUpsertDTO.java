package br.com.systec.hive.tenant.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code provider} is checked against the allowlist in the service, not by a {@code @Pattern} here.
 *
 * <p>A regex would duplicate {@link AiProvider} as a second, drifting list of provider names, and
 * the DB {@code CHECK} would be a third. One allowlist — {@code TenantLlmConfigProviders}, read in
 * one place — is the only version of this list that can still be right in six months.
 */
public record TenantLlmConfigUpsertDTO(
        @NotNull AiProvider provider,
        @NotBlank @Size(max = 255) String model
) {
}
