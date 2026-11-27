package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ES-15 / AC-12: canário no caminho SSE real — token único nunca vaza em log, trace, span ou JPA. */
class StreamingSseRedactionIT extends WebIntegrationTest {

    private static final String TOKEN = "CANARIO-SSE-REAL-8f31";
    private static final String SECRET = "SENHA-DRIVER-SSE-REAL-22b7";

    @Autowired private PublicApiProvisioningRunner provisioningRunner;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer tokenIssuer;
    @Autowired private br.com.systec.hive.publicapi.api.service.PublicChatService publicChatService;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicChatPreflight preflight;
    @Autowired private br.com.systec.hive.assistant.api.service.StreamAdmission admission;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private TurnStreamGateway turnStreamGateway;
    @MockitoBean private ChatTraceRecorder traceRecorder;
    @MockitoBean private ChatSpanSink spanSink;

    @Test
    void when_realStreamFailsWithSecretThrowable_then_onlyClosedCodeReachesTheWire() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String jwt = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "canary", tenantId, "p1");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "canary-client", "n/a", List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"))));

        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Reasoning("pensamento " + TOKEN));
            sink.accept(new TurnStreamEvent.Failed("timeout",
                    new IllegalStateException("driver: " + SECRET + " / " + TOKEN)));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());

        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            org.springframework.test.web.servlet.MockMvc sseMvc =
                    org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                            new br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1(
                                    publicChatService, preflight, turnStreamGateway, admission)).build();
            MvcResult result = br.com.systec.hive.commons.impl.security.TenantContext.runWithTenant(tenantId, () ->
                    sseMvc.perform(post("/v1/public/v1/chat/completions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .with(req -> {
                                        req.setAttribute("publicapi.pcode", List.of("p1"));
                                        req.setAttribute("publicapi.plim", Map.of(
                                                "r", 100, "c", 32000, "t", 256000, "m", 200));
                                        req.setAttribute("publicapi.keyId", "canary-key");
                                        req.setAttribute("publicapi.keylim", 100);
                                        return req;
                                    })
                                    .content("{\"prompt_code\":\"p1\","
                                            + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true}"))
                            .andExpect(request().asyncStarted()).andReturn());

            String wire = sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(wire).contains("\"code\":\"timeout\"", "\"message\":\"timeout\"", "[DONE]");
            assertThat(wire).doesNotContain(SECRET);

            // Somente o canal reasoning carrega o token; o frame de erro não o repete.
            assertThat(wire.indexOf(TOKEN)).isGreaterThanOrEqualTo(0);
            assertThat(wire.substring(wire.indexOf("\"code\":\"timeout\"") - 120)).doesNotContain(SECRET);

            assertThat(appender.list).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(TOKEN, SECRET));
            verify(traceRecorder, never()).record(any(ChatTrace.class));
            verify(spanSink, never()).publish(any());

            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM chat_message WHERE content LIKE ?", Integer.class, "%" + TOKEN + "%");
            assertThat(rows).isZero();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void when_failureIsRendered_then_toStringAndFrameUseOnlyTheClosedCode() throws Exception {
        TurnStreamEvent.Failed failed = new TurnStreamEvent.Failed("timeout",
                new IllegalStateException("segredo " + SECRET));
        assertThat(failed.toString()).doesNotContain(SECRET).contains("timeout");
        assertThat(new ObjectMapper().writeValueAsString(
                br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer.error(failed)))
                .doesNotContain(SECRET);
    }
}
