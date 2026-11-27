package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.assistant.api.service.AssistantStreamService;
import br.com.systec.hive.assistant.api.service.StreamAdmission;
import br.com.systec.hive.assistant.web.internal.v1.controller.ChatControllerV1;
import br.com.systec.hive.assistant.web.internal.v1.mapper.ChatMapper;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.integrationtest.support.SseTestSupport;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import br.com.systec.hive.publicapi.impl.provisioning.PublicApiProvisioningRunner;
import br.com.systec.hive.publicapi.impl.service.PublicChatPreflight;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import br.com.systec.hive.publicapi.web.internal.v1.controller.PublicChatControllerV1;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Emenda 3 — `Done.sessionId` vincula o primeiro turno SSE; wire público permanece idêntico. */
class DoneSessionIdIT extends WebIntegrationTest {

    @Autowired private PublicApiProvisioningRunner provisioningRunner;
    @Autowired private PublicClientTokenIssuer tokenIssuer;
    @Autowired private PublicChatService publicChatService;
    @Autowired private PublicChatPreflight preflight;
    @Autowired private AssistantStreamService assistantStreamService;
    @Autowired private br.com.systec.hive.assistant.api.service.ChatService chatService;
    @Autowired private ChatMapper chatMapper;
    @Autowired private StreamAdmission admission;
    @Autowired private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private TurnStreamGateway turnStreamGateway;
    @MockitoBean private br.com.systec.hive.ai.api.port.ChatTurnGateway chatTurnGateway;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void when_assistantDoneEmitted_then_wireSessionIdEqualsPreparedSessionId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        SseTestSupport.AssistantSetup setup = SseTestSupport.assistantSetup(
                mockMvc, jwtTestHelper, jdbc, tenantId, "done-session-id", objectMapper);
        User principal = new User();
        principal.setId(UUID.randomUUID());
        principal.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("CHAT_SEND"))));
        UUID[] preparedSessionId = new UUID[1];
        doAnswer(invocation -> {
            TurnRequest request = invocation.getArgument(0);
            preparedSessionId[0] = request.sessionId();
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3)));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
        MockMvc assistantMvc = MockMvcBuilders.standaloneSetup(new ChatControllerV1(
                chatService, chatMapper, assistantStreamService, admission)).build();
        String body = "{\"promptTemplateId\":\"" + setup.promptTemplateId() + "\",\"message\":\"oi\"}";

        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                assistantMvc.perform(post("/v1/chat/stream")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(request().asyncStarted()).andReturn());
        String wire = assistantMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(preparedSessionId[0]).isNotNull();
        assertThat(doneFrame(wire).path("sessionId").asText()).isEqualTo(preparedSessionId[0].toString());
    }

    @Test
    void when_doneSessionIdIsNull_then_assistantWireOmitsField() throws Exception {
        JsonNode data = json(AssistantSseSerializer.done(
                new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3), null)).data());
        assertThat(data.has("sessionId")).isFalse();

        // Compatibilidade: construtor de 2 args continua compilando e equivale a null.
        TurnStreamEvent.Done legacy = new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3));
        assertThat(legacy.sessionId()).isNull();
        assertThat(json(AssistantSseSerializer.done(legacy).data()).has("sessionId")).isFalse();
        assertThat(json(AssistantSseSerializer.doneWithoutUsage().data()).has("sessionId")).isFalse();
    }

    @Test
    void when_publicFinalChunk_then_sessionIdIsNeverRendered() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = SseTestSupport.provisionedToken(mockMvc, objectMapper, provisioningRunner,
                jwtTestHelper, jdbc, tokenIssuer, "done-session-public", tenantId, "p1");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "public-client", "n/a",
                List.of(new SimpleGrantedAuthority("PUBLIC_CHAT_COMPLETE"),
                        new SimpleGrantedAuthority("CHAT_SEND"))));
        UUID sessionId = UUID.randomUUID();
        doAnswer(invocation -> {
            Consumer<TurnStreamEvent> sink = invocation.getArgument(1);
            sink.accept(new TurnStreamEvent.Text("resposta"));
            sink.accept(new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3), sessionId));
            return (StreamingTurnHandle) () -> { };
        }).when(turnStreamGateway).stream(any(), any());
        MockMvc sseMvc = MockMvcBuilders.standaloneSetup(new PublicChatControllerV1(
                publicChatService, preflight, turnStreamGateway, admission)).build();
        RequestBuilder publicPost = post("/v1/public/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .with(req -> {
                    req.setAttribute("publicapi.pcode", List.of("p1"));
                    req.setAttribute("publicapi.plim", Map.of(
                            "r", 100, "c", 32000, "t", 256000, "m", 200));
                    req.setAttribute("publicapi.keyId", "done-session-public-key");
                    req.setAttribute("publicapi.keylim", 100);
                    return req;
                })
                .content("{\"model\":\"mimo-v2.5\",\"prompt_code\":\"p1\","
                        + "\"messages\":[{\"role\":\"user\",\"content\":\"oi\"}],\"stream\":true,"
                        + "\"stream_options\":{\"include_usage\":true}}");

        MvcResult result = TenantContext.runWithTenant(tenantId, () ->
                sseMvc.perform(publicPost).andReturn());
        assertThat(result.getRequest().isAsyncStarted())
                .as("status=%s body=%s", result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()).isTrue();
        String wire = sseMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(wire).contains("[DONE]");
        assertThat(wire).doesNotContain("sessionId");
        assertThat(wire).doesNotContain(sessionId.toString());

        // Mesmo com sessionId no evento, os serializadores públicos não o projetam.
        var meta = new PublicSseSerializer.Meta("chatcmpl-abc12345", 1L, "model-x");
        TurnStreamEvent.Done done = new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3), sessionId);
        assertThat(json(PublicSseSerializer.finish(meta, done)).toString()).doesNotContain("sessionId");
        assertThat(json(PublicSseSerializer.usage(meta, done)).toString()).doesNotContain("sessionId");
    }

    @Test
    void when_doneRecordAndSerializerAreInspected_then_emenda3ShapeIsPresent() throws Exception {
        Path root = StreamingITFixtures.repoRoot();
        String eventSource = Files.readString(root.resolve(
                "backend/ai/api/src/main/java/br/com/systec/hive/ai/api/domain/TurnStreamEvent.java"));
        assertThat(eventSource).contains(
                "record Done(FinishReason finishReason, TokenUsage usage, UUID sessionId)");
        assertThat(eventSource).contains("public Done(FinishReason finishReason, TokenUsage usage)");
        assertThat(eventSource).contains("this(finishReason, usage, null)");

        String serializer = Files.readString(root.resolve(
                "backend/assistant/web/internal/v1/src/main/java/br/com/systec/hive/assistant/web/internal/v1/"
                        + "serializer/AssistantSseSerializer.java"));
        assertThat(serializer).contains("data.put(\"sessionId\"");
        String errorTerminal = serializer.substring(serializer.indexOf("doneWithoutUsage"),
                serializer.indexOf("error(TurnStreamEvent.Failed"));
        assertThat(errorTerminal).doesNotContain("sessionId");
    }

    private JsonNode json(Object value) throws Exception {
        return objectMapper.readTree(objectMapper.writeValueAsString(value));
    }

    private JsonNode doneFrame(String wire) throws Exception {
        String[] lines = wire.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].equals("event:done")) {
                continue;
            }
            for (int j = i + 1; j < lines.length && !lines[j].isBlank(); j++) {
                if (lines[j].startsWith("data:")) {
                    return objectMapper.readTree(lines[j].substring("data:".length()).trim());
                }
            }
        }
        throw new AssertionError("no done frame in wire: " + wire);
    }
}
