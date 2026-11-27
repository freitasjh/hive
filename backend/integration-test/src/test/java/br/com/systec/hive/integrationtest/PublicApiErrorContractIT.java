package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-001/CT04: congela o envelope de erro `{message,type,param,code}` e os códigos do
 * contrato público antes e depois dos DTOs tipados. Nenhum handler novo é introduzido.
 */
class PublicApiErrorContractIT extends WebIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private PublicApiProvisioningRunner provisioningRunner;

    @Autowired
    private br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer tokenIssuer;

    @Autowired private br.com.systec.hive.publicapi.api.service.PublicChatService publicChatService;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicChatPreflight preflight;
    @Autowired private br.com.systec.hive.assistant.api.service.StreamAdmission admission;
    private UUID tenantId;
    @MockitoBean private TurnStreamGateway turnStreamGateway;

    private org.springframework.test.web.servlet.MockMvc sseMvc;

    @BeforeEach
    void installSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "contract-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
    }

    @BeforeEach
    void stubStream() {
        sseMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1(
                        publicChatService, preflight, turnStreamGateway, admission)).build();
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Done(FinishReason.STOP, null));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
    }

    @Test
    void when_tokenRequestHasInvalidGrant_then_errorEnvelopeIsPreserved() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_grant"))
                .andExpect(jsonPath("$.error.message").value("grant_type must be client_credentials"))
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
        System.out.println("ERROR_ENVELOPE_BASELINE " + error);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("param")).isTrue();
        assertThat(error.has("code")).isTrue();
    }

    @Test
    void when_chatHasSystemRole_then_validationLayerRejectsWithStableEnvelope() throws Exception {
        String token = provisionedToken();
        String body = "{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"system\",\"content\":\"hi\"}]}";

        MvcResult result = sseMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(req -> {
                            req.setAttribute("publicapi.pcode", java.util.List.of("p1"));
                            req.setAttribute("publicapi.plim", java.util.Map.of(
                                    "r", 100, "c", 32000, "t", 256000, "m", 200));
                            req.setAttribute("publicapi.keyId", "contract-key");
                            req.setAttribute("publicapi.keylim", 100);
                            return req;
                        })
                        .content(body))
                // @Pattern(user|assistant) rejeita na validação antes do service; código real é invalid_request.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andReturn();

        JsonNode error = objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
        System.out.println("CHAT_ERROR_ENVELOPE_BASELINE " + error);
        assertThat(error.has("message")).isTrue();
        assertThat(error.has("type")).isTrue();
        assertThat(error.has("param")).isTrue();
        assertThat(error.has("code")).isTrue();
    }

    @Test
    void when_chatHasStreamTrue_then_sseOpensWithQuotaHeaders() throws Exception {
        String token = provisionedToken();
        String body = "{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}";

        var result = br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () -> sseMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(req -> {
                            req.setAttribute("publicapi.pcode", java.util.List.of("p1"));
                            req.setAttribute("publicapi.plim", java.util.Map.of(
                                    "r", 100, "c", 32000, "t", 256000, "m", 200));
                            req.setAttribute("publicapi.keyId", "contract-key");
                            req.setAttribute("publicapi.keylim", 100);
                            return req;
                        })
                        .content(body))
                        .andReturn());
        org.assertj.core.api.Assertions.assertThat(result.getRequest().isAsyncStarted()).isTrue();
        sseMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string(
                        "X-RateLimit-Limit-Requests", org.hamcrest.Matchers.notNullValue()));
    }

    @Test
    void when_preflightFailsWithStreamTrue_then_jsonErrorAndNoEmitter() throws Exception {
        String token = provisionedToken();
        String body = "{\"prompt_code\":\"not-allowed\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],"
                + "\"stream\":true,\"stream_options\":{\"include_usage\":true}}";

        br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(post("/v1/public/v1/chat/completions")
                                .header("Authorization", "Bearer unknown-client")
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(req -> {
                                    req.setAttribute("publicapi.pcode", java.util.List.of("p1"));
                                    req.setAttribute("publicapi.plim", java.util.Map.of(
                                            "r", 100, "c", 32000, "t", 256000, "m", 200));
                                    req.setAttribute("publicapi.keyId", "contract-key");
                                    req.setAttribute("publicapi.keylim", 100);
                                    return req;
                                })
                                .content(body))
                        .andExpect(status().isForbidden()));
    }

    private String provisionedToken() throws Exception {
        tenantId = java.util.UUID.randomUUID();
        String jwt = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "contract", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "contract-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
        return jwt;
    }

}
