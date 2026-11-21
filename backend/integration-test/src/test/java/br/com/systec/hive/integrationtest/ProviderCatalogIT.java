package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /v1/providers/catalog} — o catálogo local que alimenta o seletor da UI.
 *
 * <p>Fonte da decisão (deepsearch 2026-11-20):
 * https://ui.shadcn.com/docs/ai-elements/base/model-selector — o seletor envia o modelo no body;
 * para isso ele precisa saber quais modelos existem. Só go/zen têm discovery remoto, então os
 * demais vendors vêm de {@link VendorCatalog}, sem chamada de rede.
 *
 * <p>O corpo é um ARRAY direto ({@code buildSuccessResponse(List)}), compatível com
 * {@code unwrapData<ProviderCatalogEntry[]>(body)} do frontend — não um objeto wrapper.
 */
class ProviderCatalogIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    @Test
    void when_catalogRequested_then_allVendorsAreListedWithoutManagePermission() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "catalogo-chat-user";
        seedTenantAndUser(tenantId, username, PASSWORD);
        // Apenas CHAT_SEND: nenhum AGENT_MANAGE. Um usuário comum do chat precisa deste endpoint.
        String token = jwtTestHelper.generateToken(username, tenantId, List.of("CHAT_SEND"));

        String body = mockMvc.perform(get("/v1/providers/catalog")
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(AiProvider.values().length)))
                .andExpect(jsonPath("$[?(@.provider=='OPENAI')].defaultModel", hasItem("gpt-4o-mini")))
                .andExpect(jsonPath("$[0].models[0]").value("gpt-4o-mini"))
                .andExpect(jsonPath("$[?(@.provider=='OPENCODE_ZEN')].defaultModel",
                        hasItem("claude-sonnet-4-5")))
                .andExpect(jsonPath("$[?(@.provider=='GEMINI')].configured", hasItem(false)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body.trim()).startsWith("[");
        for (AiProvider provider : AiProvider.values()) {
            assertThat(body).contains("\"provider\":\"" + provider.name() + "\"");
            assertThat(VendorCatalog.defaultModelFor(provider)).isNotNull();
        }
        assertThat(body).doesNotContain("filteredOut").doesNotContain("discoveredAt");
    }

    @Test
    void when_tenantHasCredential_then_onlyThatTenantSeesConfiguredTrue() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        String tokenA = tokenFor(tenantA, "catalogo-com-credencial");
        String tokenB = tokenFor(tenantB, "catalogo-sem-credencial");
        seedCredential(tokenA, "OPENAI");

        mockMvc.perform(get("/v1/providers/catalog").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider=='OPENAI')].configured", hasItem(true)));

        mockMvc.perform(get("/v1/providers/catalog").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider=='OPENAI')].configured", hasItem(false)));
    }

    @Test
    void when_credentialExists_then_noSecretIsExposed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "catalogo-sem-segredo");
        String secret = "sk-SEGREDO-ABC123-Unico";
        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + secret + "\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/v1/providers/catalog")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider=='OPENAI')].configured", hasItem(true)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(secret)
                .doesNotContain("SEGREDO")
                .doesNotContain("apiKey")
                .doesNotContain("last4")
                .doesNotContain("ABC123");
    }

    // ------------------------------------------------------------------ helpers

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    private void seedCredential(String token, String provider) throws Exception {
        mockMvc.perform(put("/v1/provider-credentials/" + provider)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"sk-test-fake-key\"}"))
                .andExpect(status().isOk());
    }
}
