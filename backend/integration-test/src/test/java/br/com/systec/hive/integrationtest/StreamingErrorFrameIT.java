package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ERR-05 / RF-05: erro in-band tipado, [DONE] e complete(); nunca completeWithError. */
class StreamingErrorFrameIT extends WebIntegrationTest {

    @Autowired private PublicApiProvisioningRunner provisioningRunner;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer tokenIssuer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired private br.com.systec.hive.integrationtest.support.JwtTestHelper jwtTestHelper;
    @Autowired private br.com.systec.hive.publicapi.api.service.PublicChatService publicChatService;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicChatPreflight preflight;
    @Autowired private br.com.systec.hive.assistant.api.service.StreamAdmission admission;
    @MockitoBean private TurnStreamGateway turnStreamGateway;

    @Test
    void when_providerFails_then_frameUsesClosedCodeAndStatusStays200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "error-frame", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "error-frame-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Failed("stream_failed",
                    new IllegalStateException("driver says SENHA-QUE-NAO-DEVE-VAZAR")));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());

        org.springframework.test.web.servlet.MockMvc sseMvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                        new br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1(
                                publicChatService, preflight, turnStreamGateway, admission)).build();
        var result = br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () -> sseMvc.perform(post("/v1/public/v1/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(req -> {
                            req.setAttribute("publicapi.pcode", java.util.List.of("p1"));
                            req.setAttribute("publicapi.plim", java.util.Map.of(
                                    "r", 100, "c", 32000, "t", 256000, "m", 200));
                            req.setAttribute("publicapi.keyId", "error-frame-key");
                            req.setAttribute("publicapi.keylim", 100);
                            return req;
                        })
                        .content("{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],"
                                + "\"stream\":true}"))
                        .andExpect(request().asyncStarted())
                        .andReturn());

        String body = sseMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(containsString("\"code\":\"stream_failed\"")))
                .andExpect(content().string(containsString("\"message\":\"stream_failed\"")))
                .andExpect(content().string(containsString("data: [DONE]")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("SENHA-QUE-NAO-DEVE-VAZAR");
    }

}
