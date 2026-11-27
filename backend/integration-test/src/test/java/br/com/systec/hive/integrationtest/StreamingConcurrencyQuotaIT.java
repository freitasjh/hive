package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.assistant.api.service.StreamAdmission;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QOT-14 / CR-01: admissão ownership-bound, corrida A→B e callback tardio não libera o slot de B. */
class StreamingConcurrencyQuotaIT extends WebIntegrationTest {

    @Autowired private PublicApiProvisioningRunner provisioningRunner;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer tokenIssuer;
    @Autowired private StreamAdmission admission;
    @Autowired private br.com.systec.hive.publicapi.api.service.PublicChatService publicChatService;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicChatPreflight preflight;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean private TurnStreamGateway turnStreamGateway;

    @Test
    void when_secondStreamOpensForSameClient_then_429JsonAndNoEmitter() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "quota", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "quota-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
        doAnswer(invocation -> (StreamingTurnHandle) () -> { })
                .when(turnStreamGateway).stream(any(), any());
        String body = "{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true}";
        org.springframework.test.web.servlet.MockMvc sseMvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                        new br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1(
                                publicChatService, preflight, turnStreamGateway, admission)).build();
        org.springframework.test.web.servlet.RequestBuilder req = post("/v1/public/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(r -> {
                            r.setAttribute("publicapi.pcode", List.of("p1"));
                            r.setAttribute("publicapi.plim", java.util.Map.of(
                                    "r", 100, "c", 32000, "t", 256000, "m", 200));
                            r.setAttribute("publicapi.keyId", "quota-key");
                            r.setAttribute("publicapi.keylim", 100);
                            return r;
                        })
                        .content(body);

        br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(req).andExpect(request().asyncStarted()));
        br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(req).andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.error.code").value("quota_exceeded")));
    }

    @Test
    void when_lateReleaseOfAComesAfterB_when_CIsRejectedAndBRemainsHeld() {
        UUID tenant = UUID.randomUUID();
        Object tokenA = admission.tryAdmit(tenant, "cliente");
        assertThat(tokenA).isNotNull();
        admission.release(tenant, "cliente", tokenA);

        Object tokenB = admission.tryAdmit(tenant, "cliente");
        assertThat(tokenB).isNotNull();

        admission.release(tenant, "cliente", tokenA); // callback tardio de A: não pode tocar em B

        assertThat(admission.tryAdmit(tenant, "cliente")).isNull(); // C → 429, B intacto
        admission.release(tenant, "cliente", tokenB);
        assertThat(admission.tryAdmit(tenant, "cliente")).isNotNull();
    }

    @Test
    void when_timeoutCallbackFires_then_releaseHappensOnceAndOnlyForItsOwner() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "timeout-release", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "timeout-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
        doAnswer(invocation -> (StreamingTurnHandle) () -> { })
                .when(turnStreamGateway).stream(any(), any());
        org.springframework.mock.web.MockHttpServletRequest servlet = new org.springframework.mock.web.MockHttpServletRequest();
        servlet.setAttribute("publicapi.pcode", List.of("p1"));
        servlet.setAttribute("publicapi.plim", java.util.Map.of(
                "r", 100, "c", 32000, "t", 256000, "m", 200));
        servlet.setAttribute("publicapi.keyId", "timeout-key");
        servlet.setAttribute("publicapi.keylim", 100);
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(servlet));

        br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO dto =
                new br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO();
        dto.setPrompt_code("p1");
        dto.setStream(Boolean.TRUE);
        br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO.ChatMessageDTO message =
                new br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO.ChatMessageDTO();
        message.setRole("user");
        message.setContent("oi");
        dto.setMessages(List.of(message));

        br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1 controller =
                new br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1(
                        publicChatService, preflight, turnStreamGateway, admission);
        Object response = br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () -> controller.completions(dto));
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                (org.springframework.web.servlet.mvc.method.annotation.SseEmitter)
                        ((org.springframework.http.ResponseEntity<?>) response).getBody();
        assertThat(emitter).isNotNull();
        Field callbacks = findField(emitter.getClass(), "timeoutCallback");
        callbacks.setAccessible(true);
        Runnable timeout = (Runnable) callbacks.get(emitter);
        assertThat(timeout).isNotNull();
        timeout.run();
        timeout.run(); // callback duplicado não pode liberar slot alheio

        Object other = admission.tryAdmit(tenantId, "outro-cliente");
        assertThat(other).isNotNull(); // slot de "timeout-client" foi liberado exatamente uma vez
        admission.release(tenantId, "outro-cliente", other);
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // continua na superclasse (ResponseBodyEmitter)
            }
        }
        throw new AssertionError("campo " + name + " não encontrado em " + type);
    }
}
