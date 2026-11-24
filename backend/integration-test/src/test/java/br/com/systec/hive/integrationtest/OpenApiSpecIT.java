package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate do contrato OpenAPI (ADR-001, decisão 2). Mede o documento gerado pelo springdoc
 * autenticado na chain humana. Em CT01 registra o baseline da versão 2.8.14 em Boot 4.1.0;
 * nos ciclos seguintes verifica o group {@code public}, os paths e os schemas exigidos.
 */
class OpenApiSpecIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void when_measureBaselineSpec_withHumanSession_then_recordStatusAndPaths() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "spec-it-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId);

        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        JsonNode document = body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        JsonNode paths = document.path("paths");
        List<String> pathNames = new ArrayList<>();
        paths.fieldNames().forEachRemaining(pathNames::add);
        List<String> schemas = new ArrayList<>();
        document.path("components").path("schemas").fieldNames().forEachRemaining(schemas::add);

        // Evidência de medição da hipótese CT01 (2026-11-21): status e paths observados.
        System.out.println("OPENAPI_BASELINE_MEASURE status=" + status
                + " pathCount=" + pathNames.size()
                + " paths=" + pathNames
                + " schemaCount=" + schemas.size()
                + " schemas=" + schemas);

        assertThat(status).isEqualTo(200);
        assertThat(pathNames).contains("/v1/public/auth/token", "/v1/public/v1/chat/completions");
        assertThat(schemas).contains("ChatCompletionRequestDTO", "ChatCompletionResponse", "OpenAiError");
    }

    @Test
    void when_getCanonicalPublicGroupPath_withHumanSession_then_pathsAndSchemasAreExposed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "spec-it-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId);

        MvcResult result = mockMvc.perform(get("/v3/api-docs/public")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> pathNames = new ArrayList<>();
        document.path("paths").fieldNames().forEachRemaining(pathNames::add);
        List<String> schemas = new ArrayList<>();
        document.path("components").path("schemas").fieldNames().forEachRemaining(schemas::add);

        assertThat(pathNames).contains("/v1/public/auth/token", "/v1/public/v1/chat/completions");
        assertThat(schemas).contains("ChatCompletionResponse", "OpenAiError");
    }

    @Test
    void when_getPublicGroupSpec_withHumanSession_then_pathsAndSchemasAreExposed() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "spec-it-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId);

        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .param("group", "public")
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        if (result.getResponse().getStatus() != 200) {
            System.out.println("OPENAPI_GROUP_ERROR status=" + result.getResponse().getStatus()
                    + " resolved=" + result.getResolvedException()
                    + " body=" + result.getResponse().getContentAsString());
        }
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
        List<String> pathNames = new ArrayList<>();
        document.path("paths").fieldNames().forEachRemaining(pathNames::add);
        List<String> schemas = new ArrayList<>();
        document.path("components").path("schemas").fieldNames().forEachRemaining(schemas::add);

        System.out.println("OPENAPI_PUBLIC_GROUP status=" + result.getResponse().getStatus()
                + " paths=" + pathNames + " schemas=" + schemas);

        assertThat(pathNames).containsExactlyInAnyOrder(
                "/v1/public/auth/token", "/v1/public/v1/chat/completions");
        assertThat(schemas).contains("ChatCompletionRequestDTO", "ChatCompletionResponse", "OpenAiError");
    }

    @Test
    void when_publicGroupSpecIsRead_then_bothBearerSchemesAreDeclaredOnChatOnly() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "spec-key-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId);

        MvcResult result = mockMvc.perform(get("/v3/api-docs")
                        .param("group", "public")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode schemes = document.path("components").path("securitySchemes");

        assertThat(schemes.has("bearer-key")).isTrue();
        assertThat(schemes.path("bearer-key").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("bearer-key").path("scheme").asText()).isEqualTo("bearer");
        assertThat(schemes.has("apiKeyAuth")).isTrue();
        assertThat(schemes.path("apiKeyAuth").path("type").asText()).isEqualTo("http");
        assertThat(schemes.path("apiKeyAuth").path("scheme").asText()).isEqualTo("bearer");

        String chatSecurity = document.path("paths")
                .path("/v1/public/v1/chat/completions").path("post").path("security").toString();
        assertThat(chatSecurity).contains("bearer-key").contains("apiKeyAuth");
        String tokenSecurity = document.path("paths")
                .path("/v1/public/auth/token").path("post").path("security").toString();
        assertThat(tokenSecurity).doesNotContain("apiKeyAuth");
        assertThat(document.toString()).doesNotContain("x-goog-api-key");
    }

    @Test
    void when_anonymousRequestsApiDocs_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_anonymousRequestsGroupQueryAlias_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs").param("group", "public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_anonymousRequestsCanonicalPublicGroupPath_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/v3/api-docs/public"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_anonymousRequestsSwaggerResources_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/swagger-resources/configuration/ui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_anonymousRequestsScalar_then_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/scalar/scalar-ui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void when_humanSessionRequestsApiDocs_then_returnsOk() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String username = "spec-it-" + UUID.randomUUID();
        seedTenantAndUser(tenantId, username, PASSWORD);
        String token = jwtTestHelper.generateToken(username, tenantId);

        mockMvc.perform(get("/v3/api-docs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
