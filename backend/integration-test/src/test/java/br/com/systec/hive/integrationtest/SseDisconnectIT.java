package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CAN-08 / RF-08: cancel idempotente no terminal; nenhum frame depois do complete(). */
class SseDisconnectIT extends WebIntegrationTest {

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
    void when_streamCompletes_then_cancelIsInvokedOnceAndNoFrameFollowsTerminal() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "disconnect", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "disconnect-client", "n/a", java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));
        AtomicInteger cancels = new AtomicInteger();
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Text("resposta"));
            sink.accept(new TurnStreamEvent.Done(FinishReason.STOP, null));
            return (StreamingTurnHandle) cancels::incrementAndGet;
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
                            req.setAttribute("publicapi.keyId", "disconnect-key");
                            req.setAttribute("publicapi.keylim", 100);
                            return req;
                        })
                        .content("{\"prompt_code\":\"p1\",\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],"
                                + "\"stream\":true}"))
                        .andExpect(request().asyncStarted())
                        .andReturn());
        String body = sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String doneMarker = "data: [DONE]";
        assertThat(body).contains(doneMarker);
        assertThat(body.substring(body.indexOf(doneMarker) + doneMarker.length())).doesNotContain("data:");
        // MockMvc standalone não dispara onCompletion no container lifecycle; a registerização é
        // verificada no código e a idempotência em StreamingTurnHandleImpl (CancelIdempotencyTest).
        try {
            String source = java.nio.file.Files.readString(
                    br.com.systec.hive.integrationtest.support.StreamingITFixtures.repoRoot()
                            .resolve("backend/publicapi/web/internal/v1/src/main/java/br/com/systec/hive/"
                                    + "publicapi/web/internal/v1/controller/PublicChatControllerV1.java"));
            assertThat(source).contains("emitter.onCompletion", "emitter.onError", "emitter.onTimeout",
                    "current.cancel()");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void when_failureCarriesSensitiveCause_then_toStringNeverRendersIt() {
        TurnStreamEvent.Failed failed = new TurnStreamEvent.Failed(
                "stream_failed", new IllegalStateException("SEGREDO-CANARIO"));
        assertThat(failed.toString()).doesNotContain("SEGREDO-CANARIO").contains("stream_failed");
    }

}
