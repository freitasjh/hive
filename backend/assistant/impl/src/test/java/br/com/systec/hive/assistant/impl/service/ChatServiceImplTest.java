package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.domain.ChatSession;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptStatus;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.exceptions.ChatSessionNotFoundException;
import br.com.systec.hive.assistant.api.exceptions.ConfigMissingException;
import br.com.systec.hive.assistant.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.assistant.api.exceptions.ProviderException;
import br.com.systec.hive.assistant.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import br.com.systec.hive.history.api.service.ChatMessageService;
import br.com.systec.hive.assistant.api.service.ChatSessionService;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.assistant.impl.diagnostics.FailureClassifier;
import br.com.systec.hive.assistant.impl.entity.PromptTemplateEntity;
import br.com.systec.hive.assistant.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.api.domain.TenantLlmConfig;
import br.com.systec.hive.api.exceptions.TenantLlmConfigNotFoundException;
import br.com.systec.hive.api.service.TenantLlmConfigService;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ChatSessionService chatSessionService;

    /** Fallback tenant-scoped: resolvido somente quando o cliente nao escolheu um template. */
    @Mock
    private PromptTemplateService promptTemplateService;

    /** Registro técnico `system_fallback` — criado/reusado quando o tenant não tem o seed V8. */
    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatAssistantTxService chatAssistantTxService;

    @Mock
    private TenantLlmConfigService tenantLlmConfigService;

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

    /** The real extraction: which provider/model the tenant configured, and with which credential. */
    private ProviderResolution providerResolution;

    /** The real extraction: what each outcome means, and what it costs the operator. */
    private TurnOutcomeMapper turnOutcomeMapper;

    /**
     * Built by hand rather than by {@code @InjectMocks}: the class takes a primitive configuration
     * value in its constructor and Mockito cannot supply one, so it silently gives up and reports "no
     * default constructor" for every test in the class. Naming the collaborators is also what makes a
     * new constructor argument a visible change here instead of a mystery.
     */
    private ChatServiceImpl service;

    private UUID tenantId;
    private UUID userId;
    private UUID promptTemplateId;

    /**
     * The template this test's turns are answered by. Resolved through an {@code Answer} rather than
     * a second stubbing so a test that changes the template mid-flight changes the value instead of
     * shadowing a stub and leaving Mockito to report the old one as unused.
     */
    private PromptTemplate template;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        promptTemplateId = UUID.randomUUID();
        // The two seams are the production ones. Only the factory they share stays a mock, so every
        // assertion about which model was asked, with which limits, still reads as a claim about the
        // real call rather than about a stand-in for the whole gateway.
        turnGateway = new AssistantTurnGateway(llmClientFactory);
        // O proxy AiService chama os defaults de ChatModel; mocks devolvem null sem isto.
        lenient().when(chatModel.defaultRequestParameters()).thenReturn(DefaultChatRequestParameters.EMPTY);
        lenient().when(chatModel.listeners()).thenReturn(List.of());
        // The two extracted collaborators are the production ones too: their own tests cover the rules,
        // and stubbing them here would hide a real change of behaviour behind a fake.
        providerResolution = new ProviderResolution(tenantLlmConfigService, credentialResolver);
        turnOutcomeMapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        service = newService(traceRecorder);
    }

    private ChatServiceImpl newService(ChatTraceRecorder traces) {
        return new ChatServiceImpl(providerResolution, chatSessionService, chatMessageService,
                chatAssistantTxService, chatRateLimiter, turnGateway, promptTemplateService,
                promptTemplateRepository, new TurnOutcomeMapper(traces, discoveryService));
    }

    /** The tenant's one default: vendor and model, verbatim. No fallback is exercised anywhere here. */
    private TenantLlmConfig openAiConfig() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(AiProvider.OPENAI);
        config.setModel("gpt-4o-mini");
        return config;
    }

    private TenantLlmConfig openCodeGoConfig() {
        TenantLlmConfig config = new TenantLlmConfig();
        config.setProvider(AiProvider.OPENCODE_GO);
        config.setModel("mimo-v2.5");
        return config;
    }

    private PromptTemplate openAiTemplate() {
        PromptTemplate template = new PromptTemplate();
        template.setId(promptTemplateId);
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        return template;
    }


    /** Histórico realista: o conteúdo nunca é nulo (chat_message.content é NOT NULL). */
    private static ChatMessage messageWithContent() {
        ChatMessage message = new ChatMessage();
        message.setRole(ChatRole.USER);
        message.setContent("pergunta");
        return message;
    }

    private void stubCredential() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenReturn(new CredentialResolver.ResolvedCredential("sk-fake-test-key", null));
    }

    private void stubClient(String reply) {
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), any(), any(), any(), eq("sk-fake-test-key"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder().aiMessage(AiMessage.from(reply)).build());
    }

    @Test
    void when_chat_with_noSessionId_then_createsSessionAndSavesTwoMessages() {
        ChatRequest request = newRequest();

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        session.setTenantId(tenantId);
        when(chatSessionService.create(eq(promptTemplateId), any(String.class), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
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
        verify(chatSessionService).create(eq(promptTemplateId), any(String.class), eq(tenantId), eq(userId));
        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world"));
        verify(chatAssistantTxService).saveAssistantAndTouch(eq(sessionId), eq(tenantId), eq("assistant reply"));
        // Com prompt escolhido o fallback nem é consultado: fluxo atual intacto.
        verify(promptTemplateService, never()).findActiveByCode(anyString());
    }

    /**
     * The two-parameter overload is the seam a caller with no HTTP session behind it drives, so it must
     * read neither the tenant nor the user from the environment. Both ambient sources are emptied here:
     * a reintroduced {@code TenantContext.getCurrentTenant()} or {@code SecurityUtils.currentUserId()}
     * in this path fails this test instead of quietly coupling the turn to a request thread.
     */
    @Test
    void when_chatIsDrivenByParameters_then_itReadsNothingFromTheEnvironment() {
        prepareChat();
        stubClient("resposta sem sessao");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

        SecurityContext ambient = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            ChatResponse response = service.chat(newRequest(), tenantId, userId);

            assertThat(response.getMessage()).isEqualTo("resposta sem sessao");
        } finally {
            SecurityContextHolder.setContext(ambient);
        }
    }

    @Test
    void when_chat_with_newSessionAndNoPromptTemplate_then_usesSystemFallbackAndAnswers() {
        ChatRequest request = newRequest();
        request.setPromptTemplateId(null);
        UUID fallbackId = UUID.randomUUID();
        PromptTemplate fallback = openAiTemplate();
        fallback.setId(fallbackId);
        when(promptTemplateService.findActiveByCode("system_fallback")).thenReturn(fallback);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(fallbackId);
        session.setTenantId(tenantId);
        when(chatSessionService.create(eq(fallbackId), any(String.class), eq(tenantId), eq(userId)))
                .thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(fallback);

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("resposta via fallback");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world")))
                .thenReturn(messageWithContent());
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(List.of(messageWithContent()));

        ChatResponse response = service.chat(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo(sessionId);
        assertThat(response.getMessage()).isEqualTo("resposta via fallback");
        verify(promptTemplateService).findActiveByCode("system_fallback");
        // Seed V8 presente: reuso, nenhum registro técnico é criado.
        verify(promptTemplateRepository, never()).save(any());
        // A sessão aponta para o id do fallback — nenhum template é inventado pelo chamador.
        verify(chatSessionService).create(eq(fallbackId), any(String.class), eq(tenantId), eq(userId));
        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world"));
    }

    @Test
    void when_chat_with_noActiveFallback_then_createsTechnicalRecordAndAnswers() {
        ChatRequest request = newRequest();
        request.setPromptTemplateId(null);
        UUID fallbackId = UUID.randomUUID();
        when(promptTemplateService.findActiveByCode("system_fallback"))
                .thenThrow(new PromptTemplateNotFoundException());

        PromptTemplateEntity created = new PromptTemplateEntity();
        created.setId(fallbackId);
        when(promptTemplateRepository.findByCodeAndTenantIdAndStatus(
                eq("system_fallback"), eq(tenantId), eq(PromptStatus.ACTIVE))).thenReturn(Optional.empty());
        when(promptTemplateRepository.findFirstByCodeAndTenantIdOrderByVersionDesc(
                eq("system_fallback"), eq(tenantId))).thenReturn(Optional.empty());
        when(promptTemplateRepository.save(any(PromptTemplateEntity.class))).thenReturn(created);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(fallbackId);
        session.setTenantId(tenantId);
        when(chatSessionService.create(eq(fallbackId), any(String.class), eq(tenantId), eq(userId)))
                .thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("resposta sem fallback no banco");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world")))
                .thenReturn(messageWithContent());
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(List.of(messageWithContent()));

        ChatResponse response = service.chat(request, tenantId, userId);

        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo("resposta sem fallback no banco");
        verify(promptTemplateService).findActiveByCode("system_fallback");
        // Registro técnico tenant-scoped: satisfaz a FK, texto não blank, ACTIVE v1.
        ArgumentCaptor<PromptTemplateEntity> captor = ArgumentCaptor.forClass(PromptTemplateEntity.class);
        verify(promptTemplateRepository).save(captor.capture());
        PromptTemplateEntity saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("system_fallback");
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getStatus()).isEqualTo(PromptStatus.ACTIVE);
        assertThat(saved.getVersion()).isEqualTo(1);
        assertThat(saved.getTemplate()).isEqualTo(AssistantTurnGateway.PROMPT);
        verify(chatSessionService).create(eq(fallbackId), any(String.class), eq(tenantId), eq(userId));
    }

    @Test
    void when_chat_with_fallbackOnlyDraft_then_reusesAndActivatesItWithoutDuplicating() {
        ChatRequest request = newRequest();
        request.setPromptTemplateId(null);
        when(promptTemplateService.findActiveByCode("system_fallback"))
                .thenThrow(new PromptTemplateNotFoundException());

        UUID existingId = UUID.randomUUID();
        PromptTemplateEntity draft = new PromptTemplateEntity();
        draft.setId(existingId);
        draft.setTenantId(tenantId);
        draft.setCode("system_fallback");
        draft.setTemplate("texto histórico irrelevante");
        draft.setVersion(1);
        draft.setStatus(PromptStatus.DRAFT);
        when(promptTemplateRepository.findByCodeAndTenantIdAndStatus(
                eq("system_fallback"), eq(tenantId), eq(PromptStatus.ACTIVE))).thenReturn(Optional.empty());
        when(promptTemplateRepository.findFirstByCodeAndTenantIdOrderByVersionDesc(
                eq("system_fallback"), eq(tenantId))).thenReturn(Optional.of(draft));
        when(promptTemplateRepository.save(any(PromptTemplateEntity.class)))
                .thenAnswer(i -> i.getArgument(0));

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(existingId);
        session.setTenantId(tenantId);
        when(chatSessionService.create(eq(existingId), any(String.class), eq(tenantId), eq(userId)))
                .thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("resposta reaproveitando registro");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("Hello world")))
                .thenReturn(messageWithContent());
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(List.of(messageWithContent()));

        ChatResponse response = service.chat(request, tenantId, userId);

        assertThat(response.getMessage()).isEqualTo("resposta reaproveitando registro");
        ArgumentCaptor<PromptTemplateEntity> captor = ArgumentCaptor.forClass(PromptTemplateEntity.class);
        verify(promptTemplateRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
        assertThat(captor.getValue().getStatus()).isEqualTo(PromptStatus.ACTIVE);
        // Texto antigo não-blank impediria o gateway de aplicar o default em código.
        assertThat(captor.getValue().getTemplate()).isEqualTo(AssistantTurnGateway.PROMPT);
        verify(chatSessionService).create(eq(existingId), any(String.class), eq(tenantId), eq(userId));
    }

    @Test
    void when_chat_with_selectedPromptMissing_then_throwsForTheClientNotForTheFallback() {
        ChatRequest request = newRequest();
        UUID selected = UUID.randomUUID();
        request.setPromptTemplateId(selected);

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(selected);
        when(chatSessionService.create(eq(selected), any(String.class), eq(tenantId), eq(userId)))
                .thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(null);

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(PromptTemplateNotFoundException.class);

        // Prompt escolhido inexistente é erro do cliente; nada de fallback é tocado.
        verify(promptTemplateService, never()).findActiveByCode(anyString());
        verify(promptTemplateRepository, never()).save(any());
        verify(chatMessageService, never()).save(any(), any(), any(), any());
    }

    @Test
    void when_chat_with_tenantWithoutLlmConfig_then_422TellingWhatToConfigureAndNothingPersisted() {
        ChatRequest request = newRequest();
        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(eq(promptTemplateId), any(String.class), eq(tenantId), eq(userId)))
                .thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());
        when(tenantLlmConfigService.require(tenantId)).thenThrow(new TenantLlmConfigNotFoundException());

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(TenantLlmConfigNotFoundException.class)
                // O detalhe precisa nomear a tela que corrige (Provedor e modelo), não a de credenciais.
                .hasMessageContaining("Configurações > Provedor e modelo");

        // Nenhum fallback global: sem config não há conversa, e a falha acontece antes da TX1.
        verify(chatMessageService, never()).save(any(), any(), any(), any());
        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_chat_with_existingSession_then_passesSessionIdToFactory() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = newRequest();
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(messageWithContent()));

        service.chat(request, tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), any(),
                any(), any(), eq("sk-fake-test-key"), any(), eq(sessionId));
    }

    @Test
    void when_chat_with_sessionId_then_appendsAndSendsHistory() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = newRequest();
        request.setMessage("second message");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
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
        ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor = ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        // system do template + as três mensagens da conversa
        assertThat(captor.getValue().messages()).hasSize(4);
    }

    @Test
    void when_chat_with_divergentPromptTemplateId_then_theSessionWins() {
        // O prompt é propriedade da conversa. Um cliente que ainda manda o id antigo não consegue
        // redirecionar uma conversa que já tem mensagens; o log deixa a divergência visível.
        UUID sessionId = UUID.randomUUID();
        UUID divergentId = UUID.randomUUID();

        ChatRequest request = newRequest();
        request.setMessage("hi");
        request.setSessionId(sessionId);
        request.setPromptTemplateId(divergentId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(messageWithContent()));

        service.chat(request, tenantId, userId);

        // O template resolvido é o da sessão; o divergente nunca é consultado.
        verify(chatSessionService).resolvePromptTemplate(sessionId, tenantId, userId);
        verify(chatSessionService, never()).create(any(), any(), any(), any());
    }

    @Test
    void when_chat_with_sessionTemplateMissing_then_404AndNothingPersisted() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = newRequest();
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(null);

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(br.com.systec.hive.assistant.api.exceptions.PromptTemplateNotFoundException.class);

        verify(chatMessageService, never()).save(any(), any(), any(), any());
    }

    @Test
    void when_chat_with_providerFailure_then_preservesUserMsgAndThrows502() {
        ChatRequest request = newRequest();
        request.setMessage("hello");

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenThrow(new RuntimeException("LLM down"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(messageWithContent()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ProviderException.class);

        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("hello"));
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chat_with_historyMoreThan20_then_sendsOnlyLast20() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = newRequest();
        request.setMessage("msg25");
        request.setSessionId(sessionId);

        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openAiConfig());
        stubCredential();
        stubClient("reply");
        when(chatMessageService.save(any(), any(), eq(ChatRole.USER), any())).thenReturn(messageWithContent());

        List<ChatMessage> history20 = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ChatMessage m = new ChatMessage();
            m.setContent("msg" + i);
            m.setRole(i % 2 == 0 ? ChatRole.USER : ChatRole.ASSISTANT);
            history20.add(m);
        }
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(history20);

        service.chat(request, tenantId, userId);

        ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor = ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        // system do template + as 20 últimas mensagens. O teto é da conversa; o system entra antes.
        assertThat(captor.getValue().messages()).hasSize(21);
    }

    @Test
    void when_chat_with_nonExistingSessionId_then_throw404() {
        UUID sessionId = UUID.randomUUID();
        ChatRequest request = newRequest();
        request.setSessionId(sessionId);

        when(chatSessionService.getSession(sessionId, tenantId, userId)).thenThrow(new ChatSessionNotFoundException(sessionId));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ChatSessionNotFoundException.class);
    }

    @Test
    void when_chat_withoutCredential_then_throwsConfigMissingAndNeverCallsFactory() {
        ChatRequest request = newRequest();

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());
        stubTenantConfig(openAiConfig());
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenThrow(new ConfigMissingException("OPENAI"));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ConfigMissingException.class);

        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chat_with_modelOutsideCatalog_then_throwsModelNotSupported() {
        ChatRequest request = newRequest();

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        TenantLlmConfig bad = new TenantLlmConfig();
        bad.setProvider(AiProvider.OPENAI);
        bad.setModel("llama3.2");
        stubTenantConfig(bad);

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ModelNotSupportedForProviderException.class);

        verify(credentialResolver, never()).resolve(any(), any());
        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_chat_with_rateLimited_then_throws429AndCreatesNothing() {
        doThrow(new br.com.systec.hive.assistant.api.exceptions.ChatRateLimitException())
                .when(chatRateLimiter).check(tenantId);
        ChatRequest request = newRequest();

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(br.com.systec.hive.assistant.api.exceptions.ChatRateLimitException.class);

        verify(chatSessionService, never()).create(any(), any(), any(), any());
        verify(llmClientFactory, never()).createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_chat_with_staleModel_then_evictsAndThrows422PreservingUserMsg() {
        ChatRequest request = newRequest();
        request.setMessage("hello");

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openCodeGoConfig());
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenThrow(new dev.langchain4j.exception.ModelNotFoundException("model_not_found: mimo-v2.5"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(messageWithContent()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(br.com.systec.hive.assistant.api.exceptions.ModelUnavailableException.class)
                .hasMessageContaining("mimo-v2.5")
                .hasMessageContaining("OPENCODE_GO");

        verify(discoveryService).evict(AiProvider.OPENCODE_GO);
        verify(chatMessageService).save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), eq("hello"));
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    // `isModelNotFound` used to have four tests here, when the rule was a static method on this class.
    // It belongs to the turn policy now — `agent/api/util/ModelNotFound`, with `ModelNotFoundTest`
    // covering it more fully: the same four cases plus a null, a JSON 404 that does not mention the
    // model, and a chain that wraps the model error twice. What this class has to prove about that
    // rule is not the rule itself but what the turn does with the verdict, and that is the two tests
    // above and below: a stale model evicts the vendor list and becomes a 422, and an HTML 404 does not.

    @Test
    void when_chat_with_html404Route_then_throwsProviderExceptionAndKeepsCache() {
        ChatRequest request = newRequest();

        ChatSession session = new ChatSession();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        when(chatSessionService.resolvePromptTemplate(sessionId, tenantId, userId)).thenReturn(openAiTemplate());

        stubTenantConfig(openCodeGoConfig());
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClientWithSession(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenThrow(new dev.langchain4j.exception.HttpException(404,
                        "<!DOCTYPE html><html><body>opencode.ai</body></html>"));
        when(chatMessageService.save(eq(sessionId), eq(tenantId), eq(ChatRole.USER), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(eq(sessionId), eq(tenantId))).thenReturn(List.of(messageWithContent()));

        assertThatThrownBy(() -> service.chat(request, tenantId, userId))
                .isInstanceOf(ProviderException.class)
                .isNotInstanceOf(br.com.systec.hive.assistant.api.exceptions.ModelUnavailableException.class);

        verify(discoveryService, never()).evict(any());
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    @Test
    void when_chatTrace_then_rendersOneLineWithParametersAndTimings() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), promptTemplateId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(1500, 0.42, 0.7)
                .prompt(4, 6, 2048)
                .elapsedMs(1201L)
                .usage(120, 340, 460, "STOP")
                .build();

        assertThat(LoggingChatTraceRecorder.format(trace))
                .contains("promptTemplateId=" + promptTemplateId)
                .contains("provider=OPENAI")
                .contains("model=gpt-4o-mini")
                .contains("maxTokens=1500")
                .contains("topP=0.42")
                .contains("temperature=0.7")
                .contains("historySize=4")
                .contains("promptChars=2048")
                .contains("elapsedMs=1201")
                .contains("promptTokens=120")
                .contains("completionTokens=340")
                .contains("finishReason=STOP");
    }

    @Test
    void when_chatTrace_then_theLineCarriesNoRetrievalVocabularyAtAll() {
        // `ragThreshold=not_resolved` afirmava que uma busca de similaridade rodou e não resolveu
        // um corte. Não existe retrieval no produto: o valor era inalcançável por design e ainda
        // impresso em toda linha de produção. Uma métrica que não pode ser verdadeira é pior que
        // uma métrica ausente — ela ensina a ler um vocabulário que não descreve mais o sistema.
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), promptTemplateId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(1500, 0.42, 0.7)
                .prompt(0, 1, 11)
                .elapsedMs(10L)
                .build();

        String line = LoggingChatTraceRecorder.format(trace);
        assertThat(line)
                .doesNotContain("ragUsed")
                .doesNotContain("citationsCount")
                .doesNotContain("ragOutcome")
                .doesNotContain("ragThreshold");
    }

    @Test
    void when_chatTraceWithProviderDefaultAndFailure_then_saysSoWithoutQuotingTheFailureMessage() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), promptTemplateId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(null, null, 0.7)
                .prompt(0, 1, 11)
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
        // "provider-default" é o teto que o prompt template não mandou. Contagem de token não tem default:
        // ou o provider reportou, ou não reportou. Dizer "provider-default" numa contagem sugere
        // que houve um valor padrão, e o operador perde a informação de que o provider omitiu.
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), promptTemplateId, AiProvider.OPENAI, "gpt-4o-mini")
                .agentLimits(2048, null, null)
                .prompt(0, 1, 11)
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
        ChatServiceImpl service = new ChatServiceImpl(providerResolution, chatSessionService,
                chatMessageService, chatAssistantTxService, chatRateLimiter, turnGateway,
                promptTemplateService, promptTemplateRepository,
                new TurnOutcomeMapper(failingTraces, discoveryService));
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

        assertThat(service.chat(newRequest(), tenantId, userId).getMessage()).isEqualTo("reply");
    }

    // ================================================================= C3/C4: one line per turn + reason

    @Test
    void when_chat_withLimitsConfigured_then_passesMaxTokensAndTopPToFactory() {
        prepareChat();
        PromptTemplate template = openAiTemplate();
        template.setInferenceParams(new InferenceParams(0.7, 0.42, 1500));
        withTemplate(template);
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

        service.chat(newRequest(), tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), eq(0.7),
                eq(1500), eq(0.42), eq("sk-fake-test-key"), any(), any());
    }

    @Test
    void when_chat_withUnsetLimits_then_passesNullSoProviderDefaultApplies() {
        prepareChat();
        stubClient("reply");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

        service.chat(newRequest(), tenantId, userId);

        verify(llmClientFactory).createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), eq(0.7),
                isNull(), isNull(), eq("sk-fake-test-key"), any(), any());
    }

    @Test
    void when_chat_then_theTemplatesSystemPromptLeadsThePrompt() {
        prepareChat();
        stubClient("reply");
        ChatMessage asked = new ChatMessage();
        asked.setRole(ChatRole.USER);
        asked.setContent("pergunta");
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(asked);
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(asked));

        service.chat(newRequest(), tenantId, userId);

        ArgumentCaptor<dev.langchain4j.model.chat.request.ChatRequest> captor = ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        List<?> prompt = captor.getValue().messages();
        assertThat(prompt).hasSize(2);
        assertThat(prompt.get(0)).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
        assertThat(((dev.langchain4j.data.message.SystemMessage) prompt.get(0)).text())
                .isEqualTo("Voce e um assistente.");
        assertThat(prompt.get(1)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
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
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(AiMessage.from("assistant reply"))
                .tokenUsage(new dev.langchain4j.model.output.TokenUsage(120, 340, 460))
                .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                .build());

        service.chat(newRequest(), tenantId, userId);

        ArgumentCaptor<ChatTrace> captor = ArgumentCaptor.forClass(ChatTrace.class);
        verify(traceRecorder, times(1)).record(captor.capture());
        ChatTrace trace = captor.getValue();
        assertThat(trace.sessionId()).isNotNull();
        assertThat(trace.promptTemplateId()).isEqualTo(promptTemplateId);
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
    }

    @Test
    void when_chatWithProviderFailure_then_recordsTraceWithFailureAndElapsed() {
        prepareChat();
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenThrow(new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out")));
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

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
        PromptTemplate template = openAiTemplate();
        template.setInferenceParams(new InferenceParams(0.7, null, 1500));
        withTemplate(template);
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
                .thenThrow(new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out")));
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

        assertThatThrownBy(() -> service.chat(newRequest(), tenantId, userId))
                .isInstanceOf(ProviderException.class);

        // 3 x 30s was the ~90s stall: a read timeout must not be repeated.
        verify(chatModel, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
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
        // Aplica o teto depois do stub: prepareChat() devolve um template sem maxTokens e
        // sobrescreveria o teto de 32 se fosse antes.
        PromptTemplate template = openAiTemplate();
        template.setInferenceParams(new InferenceParams(0.7, null, 32));
        withTemplate(template);

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
        PromptTemplate template = openAiTemplate();
        template.setInferenceParams(new InferenceParams(0.7, null, 256));
        withTemplate(template);
        when(llmClientFactory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(chatModel);
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(
                dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("resposta truncada mas util"))
                        .finishReason(dev.langchain4j.model.output.FinishReason.LENGTH)
                        .build());
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));

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
        when(chatModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(builder.build());
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(messageWithContent());
        when(chatMessageService.findLast20(any(), any())).thenReturn(List.of(messageWithContent()));
    }

    private void verifyNoAssistantMessageSaved() {
        // Nenhuma linha de assistente, nenhum touch na sessão: o conteúdo é NOT NULL e a gravação
        // seria um 409 de integridade em vez de um erro acionável.
        verify(chatAssistantTxService, never()).saveAssistantAndTouch(any(), any(), any());
    }

    // ================================================================= helpers

    private void stubTenantConfig(TenantLlmConfig config) {
        when(tenantLlmConfigService.require(tenantId)).thenReturn(config);
    }

    private void stubTemplate(PromptTemplate template) {
        this.template = template;
    }

    private void withTemplate(PromptTemplate template) {
        this.template = template;
    }

    private void prepareChat() {
        stubTemplate(openAiTemplate());
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setPromptTemplateId(promptTemplateId);
        when(chatSessionService.create(any(), any(), eq(tenantId), eq(userId))).thenReturn(session);
        lenient().when(chatSessionService.resolvePromptTemplate(any(), eq(tenantId), eq(userId)))
                .thenAnswer(i -> this.template);
        stubTenantConfig(openAiConfig());
        stubCredential();
    }

    private ChatRequest newRequest() {
        ChatRequest request = new ChatRequest();
        request.setPromptTemplateId(promptTemplateId);
        request.setMessage("Hello world");
        request.setSessionId(null);
        return request;
    }
}
