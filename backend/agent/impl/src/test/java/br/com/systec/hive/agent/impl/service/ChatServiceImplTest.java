package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.agent.api.port.ChatTurnGateway;
import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.domain.ChatRequest;
import br.com.systec.hive.agent.api.domain.ChatResponse;
import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.agent.api.exceptions.ConfigMissingException;
import br.com.systec.hive.agent.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.agent.api.exceptions.ProviderException;
import br.com.systec.hive.agent.api.service.AgentService;
import br.com.systec.hive.agent.api.service.ChatMessageService;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.impl.diagnostics.FailureClassifier;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private LlmClientFactory<ChatModel> llmClientFactory;

    @Mock
    private CredentialResolver credentialResolver;

    @Mock
    private AgentService agentService;

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatAssistantTxService chatAssistantTxService;

    @Mock
    private br.com.systec.hive.agent.api.service.DefaultAgentService defaultAgentService;

    @Mock
    private ChatRateLimiter chatRateLimiter;

    @Mock
    private br.com.systec.hive.provider.api.OpencodeDiscoveryService discoveryService;

    @Mock
    private ChatTraceRecorder traceRecorder;

    @Mock
    private ChatModel chatModel;

    /** The real gateway: prompt assembly, the empty-reply guard and the retry policy are production. */
    private ChatTurnGateway turnGateway;

    /**
     * Built by hand rather than by {@code @InjectMocks}: the class takes a primitive configuration
     * value in its constructor and Mockito cannot supply one, so it silently gives up and reports "no
     * default constructor" for every test in the class. Naming the collaborators is also what makes a
     * new constructor argument a visible change here instead of a mystery.
     */
    private ChatServiceImpl service;

    private UUID tenantId;
    private UUID userId;
    private UUID agentId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        // The two seams are the production ones. Only the factory they share stays a mock, so every
        // assertion about which model was asked, with which limits, still reads as a claim about the
        // real call rather than about a stand-in for the whole gateway.
        turnGateway = new LangChain4jTurnGateway(llmClientFactory);
        service = newService(traceRecorder);
    }

    private ChatServiceImpl newService(ChatTraceRecorder traces) {
        return new ChatServiceImpl(credentialResolver, agentService, chatSessionService, chatMessageService,
                chatAssistantTxService, defaultAgentService, chatRateLimiter, discoveryService,
                traces, turnGateway);
    }

    private Agent openAiAgent() {
        Agent agent = new Agent();
        agent.setId(agentId);
        agent.setProvider(AiProvider.OPENAI);
        agent.setModel("gpt-4o-mini");
        agent.setTemperature(0.7);
        return agent;
    }

    private void stubCredential() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenReturn(new CredentialResolver.ResolvedCredential("sk-fake-test-key", null));
    }

    private void stubClient(String reply) {
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), any(), any(), any(), eq("sk-fake-test-key"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from(reply)).build());
    }

    @Test
    void when_chat_with_noSessionId_then_createsSessionAndSavesTwoMessages() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("Hello world");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        session.setTenantId(tenantId);
        when(chatSessionService.create(eq(agentId), any(String.class), eq(tenantId), eq(userId))).thenReturn(session);

        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
        stubClient("assistant reply");

        ChatMessage userMsg = new ChatMessage();
        userMsg.setContent("Hello world");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world"))).thenReturn(userMsg);

        List<ChatMessage> history = List.of(userMsg);
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(history);

        ChatResponse response = service.chat(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getMessage()).isEqualTo("assistant reply");
        verify(chatSessionService).create(eq(agentId), any(String.class), eq(tenantId), eq(userId));
        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world"));
        verify(chatAssistantTxService).saveAssistantAndTouch(eq(sessionId), eq(tenantId), eq("assistant reply"));
    }

    @Test
    void when_chat_with_existingSession_then_passesSessionIdToFactory() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("stable header");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);

        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(new ChatMessage()));

        service.chat(request, tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), any(),
                any(), any(), eq("sk-fake-test-key"), any(), eq(sessionId));
    }

    @Test
    void when_chat_with_sessionId_then_appendsAndSendsHistory() {        UUID sessionId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("second message");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);

        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
        stubClient("assistant2");

        ChatMessage prevUser = new ChatMessage();
        prevUser.setContent("first");
        ChatMessage prevAssistant = new ChatMessage();
        prevAssistant.setContent("reply1");
        ChatMessage newUser = new ChatMessage();
        newUser.setContent("second message");

        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("second message"))).thenReturn(newUser);
        List<ChatMessage> history = List.of(prevUser, prevAssistant, newUser);
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(history);

        ChatResponse response = service.chat(request, tenantId, userId);

        assertThat(response.getSessionId()).isEqualTo(sessionId);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
    }

    @Test
    void when_chat_with_divergentAgentId_then_ignoresNewAgent() {
        UUID sessionId = UUID.randomUUID();
        UUID originalAgentId = UUID.randomUUID();
        UUID divergentAgentId = UUID.randomUUID();

        ChatRequest request = new ChatRequest();
        Agent divergentAgent = new Agent();
        divergentAgent.setId(divergentAgentId);
        request.setAgent(divergentAgent);
        request.setMessage("hi");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setAgentId(originalAgentId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);

        Agent originalAgent = openAiAgent();
        originalAgent.setId(originalAgentId);
        when(agentService.findById(originalAgentId)).thenReturn(originalAgent);
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(new ChatMessage()));

        service.chat(request, tenantId, userId);

        verify(agentService).findById(originalAgentId);
        verify(agentService, never()).findById(divergentAgentId);
    }

    @Test
    void when_chat_with_providerFailure_then_preservesUserMsgAndThrows502() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);

        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(chatModel);
        when(chatModel.chat(any(List.class))).thenThrow(new RuntimeException("LLM down"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(new ChatMessage()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ProviderException.class);

        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("hello"));
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chat_with_historyMoreThan20_then_sendsOnlyLast20() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("msg25");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);

        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(any(), any(), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());

        List<ChatMessage> history20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ChatMessage m = new ChatMessage();
            m.setContent("msg" + i);
            m.setRole(i % 2 == 0 ? ChatRole.USER : ChatRole.ASSISTANT);
            history20.add(m);
        }
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(history20);

        service.chat(request, tenantId, userId);

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        assertThat(captor.getValue()).hasSize(20);
    }

    @Test
    void when_chat_with_nonExistingSessionId_then_throw404() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hi");
        request.setSessionId(sessionId);

        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenThrow(new ChatSessionNotFoundException(sessionId));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_chat_withoutCredential_then_throwsConfigMissingAndNeverCallsFactory() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenThrow(new ConfigMissingException("OPENAI"));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ConfigMissingException.class);

        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chat_with_modelOutsideCatalog_then_throwsModelNotSupported() {        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);

        Agent badAgent = openAiAgent();
        badAgent.setModel("llama3.2");
        when(agentService.findById(agentId)).thenReturn(badAgent);

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ModelNotSupportedForProviderException.class);

        verify(credentialResolver, never()).resolve(any(), any());
        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_chat_with_rateLimited_then_throws429AndCreatesNothing() {
        doThrow(new br.com.systec.hive.agent.api.exceptions.ChatRateLimitException())
                .when(chatRateLimiter).check(tenantId);
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(br.com.systec.hive.agent.api.exceptions.ChatRateLimitException.class);

        verify(chatSessionService, never()).create(any(), any(), any(), any());
        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_chat_with_staleModel_then_evictsAndThrows422PreservingUserMsg() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);

        Agent goAgent = new Agent();
        goAgent.setId(agentId);
        goAgent.setProvider(AiProvider.OPENCODE_GO);
        goAgent.setModel("mimo-v2.5");
        goAgent.setTemperature(0.7);
        when(agentService.findById(agentId)).thenReturn(goAgent);
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class)))
                .thenThrow(new dev.langchain4j.exception.ModelNotFoundException("model_not_found: mimo-v2.5"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(new ChatMessage()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(br.com.systec.hive.agent.api.exceptions.ModelUnavailableException.class)
                .hasMessageContaining("mimo-v2.5")
                .hasMessageContaining("OPENCODE_GO");

        verify(discoveryService).evict(AiProvider.OPENCODE_GO);
        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("hello"));
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    // `isModelNotFound` used to have four tests here, when the rule was a static method on this class.
    // It belongs to the generation seam now, and `LangChain4jTurnGatewayTest` covers it more fully —
    // the same four cases plus a null, a JSON 404 that does not mention the model, and a chain that
    // wraps the model error twice. What this class has to prove about that rule is not the rule itself
    // but what the turn does with the verdict, and that is the two tests above and below: a stale
    // model evicts the vendor list and becomes a 422, and an HTML 404 does not.

    @Test
    void when_chat_with_html404Route_then_throwsProviderExceptionAndKeepsCache() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("hello");
        request.setSessionId(null);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);

        Agent goAgent = new Agent();
        goAgent.setId(agentId);
        goAgent.setProvider(AiProvider.OPENCODE_GO);
        goAgent.setModel("mimo-v2.5");
        goAgent.setTemperature(0.7);
        when(agentService.findById(agentId)).thenReturn(goAgent);
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class)))
                .thenThrow(new dev.langchain4j.exception.HttpException(404,
                        "<!DOCTYPE html><html><body>opencode.ai</body></html>"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(new ChatMessage()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ProviderException.class)
                .isNotInstanceOf(br.com.systec.hive.agent.api.exceptions.ModelUnavailableException.class);

        verify(discoveryService, never()).evict(any());
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chatTrace_then_rendersOneLineWithParametersAndTimings() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), agentId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(1500, 0.42, 0.7)
                .prompt(4, 6, 2048)
                .rag(false, 0, "no_link", 0.55)
                .elapsedMs(1201L)
                .usage(120, 340, 460, "STOP")
                .build();

        assertThat(LoggingChatTraceRecorder.format(trace))
                .contains("provider=OPENAI")
                .contains("model=gpt-4o-mini")
                .contains("maxTokens=1500")
                .contains("topP=0.42")
                .contains("temperature=0.7")
                .contains("historySize=4")
                .contains("promptChars=2048")
                .contains("ragUsed=false")
                .contains("citationsCount=0")
                .contains("ragOutcome=no_link")
                .contains("ragThreshold=0.55")
                .contains("elapsedMs=1201")
                .contains("promptTokens=120")
                .contains("completionTokens=340")
                .contains("finishReason=STOP");
    }

    /**
     * A turn that retrieved nothing has to say which cut-off rejected everything. The retrieval owns
     * that number — it may have fallen back to its own default — so the trace reports what it
     * resolved, not the constant this class happens to pass in.
     */
    @Test
    void when_chatTraceWithoutResolvedCutOff_then_saysNotResolvedInsteadOfInventingOne() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), agentId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(null, null, null)
                .prompt(0, 1, 11)
                .rag(false, 0, "no_link", null)
                .elapsedMs(10L)
                .build();

        assertThat(LoggingChatTraceRecorder.format(trace))
                .contains("ragThreshold=not_resolved")
                .doesNotContain("ragThreshold=provider-default");
    }

    @Test
    void when_chatTraceWithProviderDefaultAndFailure_then_saysSoWithoutQuotingTheFailureMessage() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), agentId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(null, null, 0.7)
                .prompt(0, 1, 11)
                .rag(false, 0, "error", null)
                .elapsedMs(120000L)
                .usage(null, null, null, null)
                .failure(FailureClassifier.causeOf(new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out"))))
                .build();

        String line = LoggingChatTraceRecorder.format(trace);
        assertThat(line).contains("maxTokens=provider-default").contains("topP=provider-default")
                .contains("finishReason=n/a")
                .contains("failure=HttpTimeoutException:timeout")
                .doesNotContain("read timed out");
    }

    @Test
    void when_chatTraceWithoutTokenUsage_then_reportsNotReportedNotProviderDefault() {
        // "provider-default" é o teto que o agente não mandou. Contagem de token não tem default:
        // ou o provider reportou, ou não reportou. Dizer "provider-default" numa contagem sugere
        // que houve um valor padrão, e o operador perde a informação de que o provider omitiu.
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), agentId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(2048, null, null)
                .prompt(0, 1, 11)
                .rag(false, 0, "no_link", null)
                .elapsedMs(900L)
                .usage(null, null, null, "LENGTH")
                .build();

        String line = LoggingChatTraceRecorder.format(trace);
        assertThat(line)
                .contains("maxTokens=2048")
                .contains("topP=provider-default")
                .contains("temperature=provider-default")
                .contains("promptTokens=not_reported")
                .contains("completionTokens=not_reported")
                .contains("totalTokens=not_reported")
                .doesNotContain("promptTokens=provider-default");
    }

    @Test
    void when_theTraceRecorderFails_then_chatStillAnswers() {
        prepareChat();
        ChatTraceRecorder failingTraces = trace -> {
            throw new IllegalStateException("trace down");
        };
        ChatServiceImpl service = new ChatServiceImpl(credentialResolver, agentService, chatSessionService,
                chatMessageService, chatAssistantTxService, defaultAgentService, chatRateLimiter,
                discoveryService, failingTraces, turnGateway);
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        assertThat(service.chat(newRequest(), tenantId, userId).getMessage()).isEqualTo("reply");
    }

    // ================================================================= C3/C4: one line per turn + reason

    @Test
    void when_chat_withLimitsConfigured_then_passesMaxTokensAndTopPToFactory() {
        prepareChat();
        Agent agent = openAiAgent();
        agent.setMaxTokens(1500);
        agent.setTopP(0.42);
        when(agentService.findById(agentId)).thenReturn(agent);
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        service.chat(newRequest(), tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), eq(0.7),
                eq(1500), eq(0.42), eq("sk-fake-test-key"), any(), any());
    }

    @Test
    void when_chat_withUnsetLimits_then_passesNullSoProviderDefaultApplies() {
        prepareChat();
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        service.chat(newRequest(), tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), eq(0.7),
                isNull(), isNull(), eq("sk-fake-test-key"), any(), any());
    }

    @Test
    void when_chat_then_recordsSingleTraceWithParametersTimingsAndUsage() {
        ChatMessage userMsg = new ChatMessage();
        userMsg.setContent("Hello world");
        prepareChat();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(userMsg);
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(userMsg));
        when(chatModel.chat(any(List.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from("assistant reply"))
                .tokenUsage(new dev.langchain4j.model.output.TokenUsage(120, 340, 460))
                .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                .build());

        service.chat(newRequest(), tenantId, userId);

        ArgumentCaptor<ChatTrace> captor = ArgumentCaptor.forClass(ChatTrace.class);
        verify(traceRecorder, times(1)).record(captor.capture());
        ChatTrace trace = captor.getValue();
        assertThat(trace.sessionId()).isNotNull();
        assertThat(trace.agentId()).isEqualTo(agentId);
        assertThat(trace.provider()).isEqualTo(AiProvider.OPENAI);
        assertThat(trace.model()).isEqualTo("gpt-4o-mini");
        assertThat(trace.temperature()).isEqualTo(0.7);
        assertThat(trace.historySize()).isEqualTo(1);
        assertThat(trace.promptChars()).isPositive();
        assertThat(trace.elapsedMs()).isNotNegative();
        assertThat(trace.promptTokens()).isEqualTo(120);
        assertThat(trace.completionTokens()).isEqualTo(340);
        assertThat(trace.totalTokens()).isEqualTo(460);
        assertThat(trace.finishReason()).isEqualTo("STOP");
        assertThat(trace.failure()).isNull();
        // A trace has no retrieval to report: the product does not retrieve. The fields stay on the
        // record so the rendered line keeps its shape, and they say "none" rather than "unknown".
        assertThat(trace.ragUsed()).isFalse();
        assertThat(trace.citationsCount()).isZero();
        assertThat(trace.ragOutcome()).isNull();
        assertThat(trace.ragThreshold()).isNull();
    }

    @Test
    void when_chatWithProviderFailure_then_recordsTraceWithFailureAndElapsed() {
        prepareChat();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class)))
                .thenThrow(new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out")));
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ProviderException.class);

        ArgumentCaptor<ChatTrace> captor = ArgumentCaptor.forClass(ChatTrace.class);
        verify(traceRecorder, times(1)).record(captor.capture());
        ChatTrace trace = captor.getValue();
        assertThat(trace.failure()).contains("HttpTimeoutException");
        assertThat(trace.elapsedMs()).isNotNegative();
        assertThat(trace.promptTokens()).isNull();
        assertThat(trace.finishReason()).isNull();
    }

    @Test
    void when_chatWithReadTimeout_then_callsProviderExactlyOnce() {
        prepareChat();
        Agent agent = openAiAgent();
        agent.setMaxTokens(1500);
        when(agentService.findById(agentId)).thenReturn(agent);
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class)))
                .thenThrow(new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out")));
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ProviderException.class);

        // 3 x 30s was the ~90s stall: a read timeout must not be repeated.
        verify(chatModel, times(1)).chat(any(List.class));
    }

    // ================================ RODADA 2 — resposta vazia do modelo (content NOT NULL -> 409)

    @Test
    void when_chat_withNullModelText_then_throws422AndSavesNoAssistantMessage() {
        // mimo-v2.5 com max_tokens=32: finish_reason=length, content=null, 156 chars de reasoning.
        stubEmptyModelReply(null, dev.langchain4j.model.output.FinishReason.LENGTH);

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ModelReturnedEmptyException.class)
                .hasMessageContaining("não devolveu texto");

        verifyNoAssistantMessageSaved();
    }

    @Test
    void when_chat_withEmptyModelText_then_throws422AndSavesNoAssistantMessage() {
        // Mesmo cenário, mas devolvendo "" em vez de null — o vazio exato varia por modelo.
        stubEmptyModelReply("", null);

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ModelReturnedEmptyException.class);

        verifyNoAssistantMessageSaved();
    }

    @Test
    void when_chat_withBlankModelText_then_throws422AndSavesNoAssistantMessage() {
        stubEmptyModelReply("   \n\t ", dev.langchain4j.model.output.FinishReason.LENGTH);

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ModelReturnedEmptyException.class);

        verifyNoAssistantMessageSaved();
    }

    @Test
    void when_chat_withEmptyModelText_then_keepsUserMessageSoItCanBeResent() {
        stubEmptyModelReply(null, dev.langchain4j.model.output.FinishReason.LENGTH);

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ModelReturnedEmptyException.class);

        // A mensagem do usuário foi salva em TX1, antes da chamada ao provider. Ela é o contexto que
        // o usuário precisa para reenviar: apagá-la deixaria a tela vazia sem explicação.
        verify(chatMessageService).save(any(), eq(tenantId), eq(ChatRole.USER), eq("Hello world"));
    }

    @Test
    void when_chat_withEmptyModelText_then_recordsTraceWithEmptyReasonAndLimits() {
        stubEmptyModelReply(null, dev.langchain4j.model.output.FinishReason.LENGTH);
        // Aplica o teto depois do stub: prepareChat() devolve um agente sem maxTokens e sobrescreveria
        // o agente com 32 se fosse antes.
        Agent agent = openAiAgent();
        agent.setMaxTokens(32);
        when(agentService.findById(agentId)).thenReturn(agent);

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ModelReturnedEmptyException.class);

        ArgumentCaptor<ChatTrace> captor = ArgumentCaptor.forClass(ChatTrace.class);
        verify(traceRecorder, times(1)).record(captor.capture());
        ChatTrace trace = captor.getValue();
        assertThat(trace.failure()).isEqualTo("empty_model_response");
        assertThat(trace.finishReason()).isEqualTo("LENGTH");
        assertThat(trace.maxTokens()).isEqualTo(32);
    }

    @Test
    void when_chat_withLengthFinishReasonAndText_then_stillSavesAssistantAndAnswers() {
        // mimo-v2.5 com max_tokens=256: finish_reason=length mas 610 chars de conteúdo. Truncamento
        // é sucesso normal — não pode virar 422.
        prepareChat();
        Agent agent = openAiAgent();
        agent.setMaxTokens(256);
        when(agentService.findById(agentId)).thenReturn(agent);
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(List.class))).thenReturn(
                dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("resposta truncada mas util"))
                        .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH)
                        .build());
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));

        ChatResponse response = service.chat(newRequest(), tenantId, userId);

        assertThat(response.getMessage()).isEqualTo("resposta truncada mas util");
        verify(chatAssistantTxService).saveAssistantAndTouch(any(), eq(tenantId), eq("resposta truncada mas util"));
    }

    private void stubEmptyModelReply(String text, dev.langchain4j.model.output.FinishReason finishReason) {
        prepareChat();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        var builder = dev.langchain4j.model.chat.response.ChatResponse.builder();
        if (text == null) {
            // Reproduz o shape real do upstream: OpenAiUtils faz AiMessage.builder().text(null) quando
            // o provider devolve content nulo. O construtor AiMessage(String) rejeitaria null, então
            // o builder é obrigatório aqui — é o mesmo caminho que o mapper exercita em produção.
            builder.aiMessage(AiMessage.builder().text(null).build());
        } else {
            builder.aiMessage(AiMessage.from(text));
        }
        if (finishReason != null) {
            builder.finishReason(finishReason);
        }
        when(chatModel.chat(any(List.class))).thenReturn(builder.build());
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(new ChatMessage()));
    }

    private void verifyNoAssistantMessageSaved() {
        // Nenhuma linha de assistente, nenhum touch na sessão: o conteúdo é NOT NULL e a gravação
        // seria um 409 de integridade em vez de um erro acionável.
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    // ================================================================= helpers

    private void prepareChat() {
        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(agentService.findById(agentId)).thenReturn(openAiAgent());
        stubCredential();
    }

    private ChatRequest newRequest() {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage("Hello world");
        request.setSessionId(null);
        return request;
    }
}
