package br.com.systec.hive.assistant.web.internal.v1.controller;

import br.com.systec.hive.assistant.api.domain.ProviderCredential;
import br.com.systec.hive.assistant.api.service.ProviderCredentialService;
import br.com.systec.hive.assistant.api.service.ValidateRateLimitPolicy;
import br.com.systec.hive.assistant.web.internal.v1.dto.FilteredModelDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.ProviderModelsDTO;
import br.com.systec.hive.assistant.web.internal.v1.mapper.ProviderModelsDTOMapper;
import br.com.systec.hive.commons.api.tools.exceptions.ControllerExceptionHandler;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.DiscoveredModels;
import br.com.systec.hive.provider.api.FilteredModel;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProviderModelsControllerV1Test {

    @Mock
    OpencodeDiscoveryService discoveryService;
    @Mock
    ValidateRateLimitPolicy rateLimiter;
    @Mock
    ProviderCredentialService credentialService;
    @Spy
    ProviderModelsDTOMapper mapper = Mappers.getMapper(ProviderModelsDTOMapper.class);

    @InjectMocks
    ProviderModelsControllerV1 controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    private DiscoveredModels discovered() {
        return new DiscoveredModels(
                List.of("mimo-v2.5"),
                List.of(new FilteredModel("grok-4.5", "DENYLISTED")),
                Instant.parse("2026-09-25T19:00:00Z"));
    }

    @Test
    void when_models_with_go_then_returnsFilteredList() throws Exception {
        when(discoveryService.discover(AiProvider.OPENCODE_GO)).thenReturn(discovered());
        UUID tenantId = UUID.randomUUID();

        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/providers/go/models").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.models[0]").value("mimo-v2.5"))
                        .andExpect(jsonPath("$.filteredOut[0].id").value("grok-4.5"))
                        .andExpect(jsonPath("$.filteredOut[0].reason").value("DENYLISTED"))
                        .andExpect(jsonPath("$.cachedAt").exists());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        verify(discoveryService, never()).evict(any());
    }

    @Test
    void when_models_with_refresh_then_evictsBeforeDiscover() throws Exception {
        when(discoveryService.discover(AiProvider.OPENCODE_ZEN)).thenReturn(discovered());
        UUID tenantId = UUID.randomUUID();

        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/providers/zen/models")
                                .param("refresh", "true")
                                .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.models[0]").value("mimo-v2.5"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        verify(discoveryService).evict(AiProvider.OPENCODE_ZEN);
        verify(discoveryService).discover(AiProvider.OPENCODE_ZEN);
    }

    @Test
    void when_models_with_unsupportedProvider_then_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();

        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/providers/openai/models").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        verify(discoveryService, never()).discover(any());
    }

    @Test
    void when_models_with_rateLimited_then_returns429() throws Exception {
        UUID tenantId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new br.com.systec.hive.assistant.api.exceptions.ValidateRateLimitException())
                .when(rateLimiter).check(any());

        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/providers/go/models").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isTooManyRequests());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Test
    void when_catalog_then_returnsDirectArrayFromLocalCatalogOnly() throws Exception {
        UUID tenantId = UUID.randomUUID();
        ProviderCredential openai = new ProviderCredential();
        openai.setProvider(AiProvider.OPENAI);
        openai.setConfigured(true);
        when(credentialService.listStatuses(tenantId)).thenReturn(List.of(openai));

        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/providers/catalog").accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(AiProvider.values().length)))
                        .andExpect(jsonPath("$[0].provider").exists())
                        .andExpect(jsonPath("$[0].models").exists())
                        .andExpect(jsonPath("$[0].defaultModel").exists())
                        .andExpect(jsonPath("$[0].configured").exists())
                        .andExpect(jsonPath("$[0].apiKey").doesNotExist());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });

        // Catálogo local: nem discovery remoto, nem rate limit de discovery.
        verify(discoveryService, never()).discover(any());
        verify(rateLimiter, never()).check(any());
    }

    @Test
    void when_toDto_with_domain_then_mapsAllFields() {
        ProviderModelsDTO dto = mapper.toDto(discovered());

        org.assertj.core.api.Assertions.assertThat(dto.models()).containsExactly("mimo-v2.5");
        org.assertj.core.api.Assertions.assertThat(dto.filteredOut())
                .containsExactly(new FilteredModelDTO("grok-4.5", "DENYLISTED"));
        org.assertj.core.api.Assertions.assertThat(dto.cachedAt()).isEqualTo(Instant.parse("2026-09-25T19:00:00Z"));
    }
}
