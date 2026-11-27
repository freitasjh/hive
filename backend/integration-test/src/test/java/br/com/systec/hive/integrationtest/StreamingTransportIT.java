package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.assistant.api.service.AssistantStreamService;
import br.com.systec.hive.assistant.api.service.StreamAdmission;
import br.com.systec.hive.assistant.web.internal.v1.controller.ChatControllerV1;
import br.com.systec.hive.assistant.web.internal.v1.mapper.ChatMapper;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.service.PublicChatPreflight;
import br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSE-01 / RF-01: SseEmitter nos dois controllers, produces text/event-stream e complete() terminal.
 * Dispatch assíncrono usa MockMvc standalone: o filtro one-per-request do Spring Security não
 * reautentica o ASYNC_DISPATCH no MockMvc (limitação do harness, não do transporte).
 */
class StreamingTransportIT extends WebIntegrationTest {

    @Autowired private PublicApiProvisioningRunner provisioningRunner;
    @Autowired private br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer tokenIssuer;
    @Autowired private PublicChatService publicChatService;
    @Autowired private PublicChatPreflight preflight;
    @Autowired private AssistantStreamService assistantStreamService;
    @Autowired private br.com.systec.hive.assistant.api.service.ChatService chatService;
    @Autowired private ChatMapper chatMapper;
    @Autowired private StreamAdmission admission;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean private TurnStreamGateway turnStreamGateway;
    @MockitoBean private br.com.systec.hive.ai.api.port.ChatTurnGateway chatTurnGateway;

    private MockMvc sseMvc;
    private String token;
    private UUID tenantId;

    @BeforeEach
    void setUp() throws Exception {
        sseMvc = MockMvcBuilders.standaloneSetup(new PublicChatControllerV1(
                        publicChatService, preflight, turnStreamGateway, admission)).build();
        when(chatTurnGateway.execute(any())).thenReturn(
                TurnReply.answered("resposta", 1, 1, 1, 1, 2, "STOP"));
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Reasoning("raciocinio"));
            sink.accept(new TurnStreamEvent.Text("resposta"));
            sink.accept(new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3)));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
        tenantId = UUID.randomUUID();
        token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner, jwtTestHelper,
                jdbcTemplate, tokenIssuer, "transport", tenantId, "p1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "transport-client", "n/a",
                List.of(new SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"),
                        new SimpleGrantedAuthority("CHAT_SEND"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private RequestBuilder publicPost(String body) {
        return post("/v1/public/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .with(req -> {
                    req.setAttribute("publicapi.pcode", List.of("p1"));
                    req.setAttribute("publicapi.plim", Map.of(
                            "r", 100, "c", 32000, "t", 256000, "m", 200));
                    req.setAttribute("publicapi.keyId", "transport-key");
                    req.setAttribute("publicapi.keylim", 100);
                    return req;
                })
                .content(body);
    }

    @Test
    void when_streamTrue_then_asyncStartedAndTextEventStreamWithDoneSentinel() throws Exception {
        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(publicPost("{\"model\":\"mimo-v2.5\",\"prompt_code\":\"p1\","
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true}"))
                        .andExpect(request().asyncStarted()).andReturn());

        String wire = sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andReturn().getResponse().getContentAsString();
        assertThat(wire).contains("reasoning_content", "\"content\":\"resposta\"", "[DONE]");
    }

    @Test
    void when_streamIsAbsent_then_jsonContractIsUnchangedAndNoEmitterOpens() throws Exception {
        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(publicPost("{\"model\":\"mimo-v2.5\",\"prompt_code\":\"p1\","
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}]}"))
                        .andReturn());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    @Test
    void when_assistantStreamIsRequested_then_namedEventsAreEmitted() throws Exception {
        UUID assistantTenant = UUID.randomUUID();
        SseTestSupport.AssistantSetup setup = SseTestSupport.assistantSetup(
                mockMvc, jwtTestHelper, jdbcTemplate, assistantTenant, "sse-transport", objectMapper);
        User principal = new User();
        principal.setId(UUID.randomUUID());
        principal.setTenantId(assistantTenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("CHAT_SEND"))));
        MockMvc assistantMvc = MockMvcBuilders.standaloneSetup(new ChatControllerV1(
                        chatService, chatMapper, assistantStreamService, admission)).build();
        String body = "{\"promptTemplateId\":\"" + setup.promptTemplateId() + "\",\"message\":\"oi\"}";

        MvcResult result = TenantContext.runWithTenant(assistantTenant, () ->
                assistantMvc.perform(post("/v1/chat/stream")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(request().asyncStarted()).andReturn());
        String wire = assistantMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(wire).contains("event:thinking", "event:text", "event:done");
        Integer assistantRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM chat_message WHERE role = 'ASSISTANT' AND content = ?",
                Integer.class, "resposta");
        assertThat(assistantRows).isEqualTo(1);
    }

    @Test
    void when_assistantLacksChatSend_then_403JsonBeforeEmitter() throws Exception {
        UUID other = UUID.randomUUID();
        seedTenantAndUser(other, "sse-no-permission", "it-secret-pw");
        String jwt = jwtTestHelper.generateToken("sse-no-permission", other, List.of());
        mockMvc.perform(post("/v1/chat/stream")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void when_publicStreamExceedsFrameCeiling_then_terminalErrorAndDoneAreEmitted() throws Exception {
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            for (int i = 0; i < 2005; i++) {
                sink.accept(new TurnStreamEvent.Reasoning("t" + i));
            }
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(publicPost("{\"prompt_code\":\"p1\","
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true}"))
                        .andExpect(request().asyncStarted()).andReturn());
        String wire = sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(wire).contains("\"code\":\"frame_limit\"", "[DONE]");
    }

    @Test
    void when_assistantStreamExceedsFrameCeiling_then_errorAndDoneAreEmitted() throws Exception {
        UUID assistantTenant = UUID.randomUUID();
        SseTestSupport.AssistantSetup setup = SseTestSupport.assistantSetup(
                mockMvc, jwtTestHelper, jdbcTemplate, assistantTenant, "sse-cap", objectMapper);
        User principal = new User();
        principal.setId(UUID.randomUUID());
        principal.setTenantId(assistantTenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("CHAT_SEND"))));
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            for (int i = 0; i < 2005; i++) {
                sink.accept(new TurnStreamEvent.Text("t" + i));
            }
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
        MockMvc assistantMvc = MockMvcBuilders.standaloneSetup(new ChatControllerV1(
                chatService, chatMapper, assistantStreamService, admission)).build();

        MvcResult result = TenantContext.runWithTenant(assistantTenant, () ->
                assistantMvc.perform(post("/v1/chat/stream").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"promptTemplateId\":\"" + setup.promptTemplateId()
                                        + "\",\"message\":\"oi\"}"))
                        .andExpect(request().asyncStarted()).andReturn());
        String wire = assistantMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(wire).contains("event:error", "frame_limit", "event:done");
    }

    @Test
    void when_streamOptionsHasUnknownNestedField_then_streamStillOpens() throws Exception {
        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(publicPost("{\"model\":\"mimo-v2.5\",\"prompt_code\":\"p1\","
                                + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true,"
                                + "\"stream_options\":{\"include_usage\":true,\"future_flag\":1,\"ignoreUnknown\":true}}"))
                        .andExpect(request().asyncStarted()).andReturn());
        sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }
}
