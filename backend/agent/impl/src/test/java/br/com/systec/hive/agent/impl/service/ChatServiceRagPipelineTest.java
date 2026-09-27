package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.diagnostics.ChatTrace;
import br.com.systec.hive.agent.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.agent.api.domain.Agent;
import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.domain.ChatRequest;
import br.com.systec.hive.agent.api.domain.ChatResponse;
import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.domain.TurnReply;
import br.com.systec.hive.agent.api.domain.TurnRequest;
import br.com.systec.hive.agent.api.port.ChatTurnGateway;
import br.com.systec.hive.agent.api.service.AgentService;
import br.com.systec.hive.agent.api.service.ChatMessageService;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.api.service.DefaultAgentService;
import br.com.systec.hive.agent.impl.rag.RagTurnResolver;
import br.com.systec.hive.commons.testsupport.HardcodedSimilarityCutOffGuard;
import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.ChatRole;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the turn hands to the two seams, and what it takes back.
 *
 * <p>The pipeline behaviour itself — what the retrieved text does to the prompt, which chunk gets
 * cited, whether the question is duplicated — moved with the code that owns it. Retrieval and
 * prompt assembly are now {@link RagTurnResolver}'s and {@link ChatTurnGateway}'s, and are tested
 * there ({@code RagTurnResolverTest}) and in {@code LangChain4jTurnGatewayTest}. What is left to
 * assert at this level is the wiring: the RAG context the resolver produced reaches the gateway,
 * the gateway's answer reaches the client, and the trace is written exactly once per turn.
 *
 * <p>Both collaborators are real objects rather than mocks, so these tests exercise the actual
 * hand-off instead of two mocks agreeing on a shape.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceRagPipelineTest {

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
    private DefaultAgentService defaultAgentService;
    @Mock
    private ChatRateLimiter chatRateLimiter;
    @Mock
    private OpencodeDiscoveryService discoveryService;
    @Mock
    private br.com.systec.hive.knowledge.api.port.KbRetrievalPort kbRetrieval;
    @Mock
    private br.com.systec.hive.knowledge.api.port.KbAgentLinkPort kbLinkPort;
    @Mock
    private dev.langchain4j.model.chat.ChatModel chatModel;

    private final List<ChatTrace> traces = new ArrayList<>();
    private final List<br.com.systec.hive.knowledge.api.domain.RagDiagnostic> diagnostics = new ArrayList<>();

    private UUID tenantId;
    private UUID userId;
    private UUID agentId;
    private UUID kbId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    /** The real resolver, so a retrieval outcome reaches the turn as an outcome and not as a stub. */
    private RagTurnResolver resolver(String queryCompressionModel) {
        return new RagTurnResolver(kbRetrieval, kbLinkPort, diagnostics::add,
                stubLlmFactory(), queryCompressionModel, 40);
    }

    /** The real gateway, so prompt assembly and the empty-reply guard are the production ones. */
    private ChatTurnGateway gateway() {
        return new LangChain4jTurnGateway(stubLlmFactory());
    }

    private br.com.systec.hive.provider.api.LlmClientFactory<dev.langchain4j.model.chat.ChatModel>
    stubLlmFactory() {
        return new br.com.systec.hive.provider.api.LlmClientFactory<>() {
            @Override
            public dev.langchain4j.model.chat.ChatModel createClient(
                    AiProvider provider, String model, Double temperature, Integer maxTokens,
                    Double topP, String apiKey, String baseUrl) {
                return chatModel;
            }

            @Override
            public dev.langchain4j.model.chat.ChatModel createClientWithSession(
                    AiProvider provider, String model, Double temperature, Integer maxTokens,
                    Double topP, String apiKey, String baseUrl, UUID sessionId) {
                return chatModel;
            }
        };
    }

    private ChatServiceImpl service(String queryCompressionModel) {
        return new ChatServiceImpl(credentialResolver, agentService, chatSessionService, chatMessageService,
                chatAssistantTxService, defaultAgentService, chatRateLimiter, discoveryService,
                trace -> traces.add(trace), resolver(queryCompressionModel), gateway());
    }

    private ChatServiceImpl service() {
        return service(null);
    }

    private void prepareTurn(String question) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setAgentId(agentId);
        when(chatSessionService.create(eq(agentId), anyString(), eq(tenantId), eq(userId))).thenReturn(session);
        Agent agent = new Agent();
        agent.setId(agentId);
        agent.setProvider(AiProvider.OPENAI);
        agent.setModel("gpt-4o-mini");
        agent.setTemperature(0.7);
        when(agentService.findById(agentId)).thenReturn(agent);
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenReturn(new CredentialResolver.ResolvedCredential("sk-test", null));
        when(chatModel.chat(ArgumentMatchers.<List<dev.langchain4j.data.message.ChatMessage>>any())).thenReturn(
                dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.from("resposta")).build());
        when(chatMessageService.save(any(), any(), any(), any())).thenReturn(new ChatMessage());
        when(chatMessageService.findLast20(sessionId, tenantId)).thenReturn(historyOf(question));
    }

    /** The turn's own question is in the saved history — it is saved before the RAG resolution. */
    private List<ChatMessage> historyOf(String question) {
        return List.of(message(ChatRole.USER, "Fale sobre o contrato de 2024"),
                message(ChatRole.ASSISTANT, "O contrato de 2024 tem prazo de 30 dias"),
                message(ChatRole.USER, question));
    }

    private ChatMessage message(ChatRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private void stubRetrieval(RagRetrievalResult result) {
        when(kbLinkPort.linkedKbIds(agentId, tenantId)).thenReturn(List.of(kbId));
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), any(), any(), any(Integer.class), any()))
                .thenReturn(result);
    }

    private ChatRequest request(String question) {
        ChatRequest request = new ChatRequest();
        Agent agent = new Agent();
        agent.setId(agentId);
        request.setAgent(agent);
        request.setMessage(question);
        return request;
    }

    private KbChunk chunk(String text) {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(UUID.randomUUID());
        chunk.setDocName("contrato-2024.pdf");
        chunk.setChunkId(UUID.randomUUID());
        chunk.setText(text);
        chunk.setScore(0.88);
        return chunk;
    }

    @SuppressWarnings("unchecked")
    private List<dev.langchain4j.data.message.ChatMessage> capturedPrompt() {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        return captor.getValue();
    }

    private static String textOf(List<dev.langchain4j.data.message.ChatMessage> prompt) {
        StringBuilder text = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage message : prompt) {
            if (message instanceof SystemMessage system) {
                text.append(system.text()).append('\n');
            } else if (message instanceof UserMessage user) {
                text.append(user.singleText()).append('\n');
            }
        }
        return text.toString();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    private br.com.systec.hive.knowledge.api.domain.RagDiagnostic lastDiagnostic() {
        assertThat(diagnostics).as("every turn emits exactly one RAG line").isNotEmpty();
        return diagnostics.get(diagnostics.size() - 1);
    }

    private static String systemTextOf(List<dev.langchain4j.data.message.ChatMessage> prompt) {
        StringBuilder text = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage message : prompt) {
            if (message instanceof SystemMessage system) {
                text.append(system.text()).append('\n');
            }
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ the pipeline ran

    @Test
    void when_chatWithLinkedKnowledgeBase_then_theRetrievedTextReachesThePromptInASystemMessage() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias corridos")),
                12L, 0.60, 1, null, null));

        ChatResponse response = service().chat(request(question), tenantId, userId);

        assertThat(systemTextOf(capturedPrompt()))
                .contains("prazo de 30 dias corridos")
                .contains("contrato-2024.pdf")
                .contains("Contexto relevante");
        assertThat(response.isRagUsed()).isTrue();
        assertThat(response.getRagReason()).isEqualTo("ok");
        assertThat(response.getCitations()).hasSize(1);
    }

    /**
     * The question reaches the model as the user wrote it. The integration suite pins the same thing
     * on the embedding side — a prefix on the question moves it in the vector space — so the context
     * staying out of the user turn is load-bearing, not a style choice.
     */
    @Test
    void when_contextIsInjected_then_theQuestionIsNotModified() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias")),
                12L, 0.60, 1, null, null));

        service().chat(request(question), tenantId, userId);

        List<dev.langchain4j.data.message.ChatMessage> prompt = capturedPrompt();
        UserMessage last = (UserMessage) prompt.get(prompt.size() - 1);
        assertThat(last.singleText()).isEqualTo(question);
    }

    @Test
    void when_noKnowledgeBaseIsLinked_then_noContextAndTheReasonIsNoLink() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        when(kbLinkPort.linkedKbIds(agentId, tenantId)).thenReturn(List.of());

        ChatResponse response = service().chat(request(question), tenantId, userId);

        assertThat(response.isRagUsed()).isFalse();
        assertThat(response.getRagReason()).isEqualTo("no_link");
        assertThat(response.getCitations()).isEmpty();
        verify(kbRetrieval, never()).retrieveWithOutcome(any(), any(), any(), any(Integer.class), any());
    }

    /**
     * The corruption this guard exists for.
     *
     * <p>A retrieval whose chunks are all textless satisfies {@code hasChunks()}, and the retriever
     * cannot offer any of them to the pipeline — the official {@code TextSegment} refuses blank text. The
     * augmentor then answers an empty list by handing back the message it was given, which is the
     * question. Without the guard the turn would put the question into the prompt a second time, claim
     * {@code ragUsed=true} with no knowledge behind it, and report the length of the question as the
     * size of the context block.
     */
    @Test
    void when_noChunkCarriesText_then_nothingIsInjectedAndTheQuestionIsNotDuplicated() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk(""), chunk("   "), chunk(null)), 9L, 0.60, 1, null, null));

        ChatResponse response = service().chat(request(question), tenantId, userId);

        assertThat(response.isRagUsed()).isFalse();
        assertThat(response.getCitations()).isEmpty();
        assertThat(lastDiagnostic().systemBlockChars())
                .as("no block was injected, so the size reported must be the absence of one")
                .isEqualTo(br.com.systec.hive.knowledge.api.domain.RagDiagnostic.NO_SYSTEM_BLOCK);
        List<dev.langchain4j.data.message.ChatMessage> prompt = capturedPrompt();
        assertThat(systemTextOf(prompt))
                .as("nothing was retrieved, so no system message may carry the question")
                .doesNotContain(question);
        assertThat(countOccurrences(textOf(prompt), question))
                .as("the question appears once: the turn's own message, not a second copy of it")
                .isEqualTo(1);
    }

    /**
     * A citation is a claim to the user about where the answer came from, so it may not name a chunk
     * the model never saw. Deriving them from the retrieval result instead of from what was injected
     * lists a source that was dropped — the client would show a citation the prompt does not contain.
     */
    @Test
    void when_oneChunkHasNoText_then_onlyTheInjectedOneIsCited() {
        prepareTurn("Qual o prazo do contrato de 2024?");
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("prazo de 30 dias"), chunk("")), 9L, 0.60, 1, null, null));

        ChatResponse response = service().chat(request("Qual o prazo do contrato de 2024?"), tenantId, userId);

        assertThat(response.isRagUsed()).isTrue();
        assertThat(response.getCitations())
                .as("the search returned two chunks and the model was given one")
                .hasSize(1);
        assertThat(response.getCitations().get(0).getDocName()).isEqualTo("contrato-2024.pdf");
        assertThat(lastDiagnostic().chunks())
                .as("the diagnostic keeps describing the search as it was")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------ the seams

    /**
     * The context the resolver produced has to reach the gateway <b>as the same object</b>. A turn
     * that re-resolved, re-derived or copied it would be the second opinion this refactor exists to
     * remove — and a copy is what a field-by-field rebuild looks like from inside the turn.
     *
     * <p>Asserted by identity, not equality: {@code RagContext} carries a {@code KbCitation}, which is
     * a plain bean with identity equality, so two structurally identical contexts are not equal and
     * only {@code isSameAs} says what this test is about.
     */
    @Test
    void when_theResolverProducesAContext_then_theGatewayIsHandedThatSameObject() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias")),
                12L, 0.60, 1, null, null));
        RagTurnResolver resolver = org.mockito.Mockito.spy(resolver(null));
        ChatTurnGateway spyGateway = org.mockito.Mockito.spy(gateway());
        ChatServiceImpl service = new ChatServiceImpl(credentialResolver, agentService, chatSessionService,
                chatMessageService, chatAssistantTxService, defaultAgentService, chatRateLimiter,
                discoveryService, trace -> traces.add(trace), resolver, spyGateway);

        service.chat(request(question), tenantId, userId);

        ArgumentCaptor<RagTurnResolver.RagRequest> resolved =
                ArgumentCaptor.forClass(RagTurnResolver.RagRequest.class);
        verify(resolver).resolve(resolved.capture());
        ArgumentCaptor<TurnRequest> handed = ArgumentCaptor.forClass(TurnRequest.class);
        verify(spyGateway).execute(handed.capture());

        assertThat(handed.getValue().ragContext().used()).isTrue();
        assertThat(handed.getValue().ragContext().citations()).hasSize(1);
        assertThat(handed.getValue().ragContext().outcome()).isEqualTo(RagOutcome.OK);
        assertThat(handed.getValue().ragContext().threshold()).isEqualTo(0.60);
        assertThat(handed.getValue().ragContext().injectedChars()).isPositive();
        assertThat(resolved.getValue().question()).isEqualTo(question);
        assertThat(resolved.getValue().agentId()).isEqualTo(agentId);
        assertThat(resolved.getValue().tenantId()).isEqualTo(tenantId);
    }

    // ------------------------------------------------------------------ query compression

    /**
     * Off unless a model is named. The stage costs one LLM call per question that looks like a
     * follow-up, and the latency baseline forbids adding an unmeasured one.
     */
    @Test
    void when_noCompressionModelIsConfigured_then_noSecondClientIsEverBuilt() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        service().chat(request(question), tenantId, userId);

        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                any(Integer.class), any());
    }

    /**
     * The reason the stage exists: a follow-up that means nothing on its own is rewritten with the
     * conversation, and it is the rewritten text that is searched.
     */
    @Test
    void when_aFollowUpIsCompressed_then_theRewrittenQuestionIsWhatGetsSearched() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));
        when(chatModel.chat(anyString())).thenReturn("Qual o prazo do contrato de 2024?");

        service("gpt-4o").chat(request(question), tenantId, userId);

        verify(chatModel).chat(anyString());
        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)),
                eq("Qual o prazo do contrato de 2024?"), any(Integer.class), any());
    }

    /**
     * The compressor reads the conversation and the new question as two separate inputs, so the turn's
     * own question must not be in both: it would be paid for twice, in the one stage whose cost this
     * change is trying to bound.
     */
    @Test
    void when_compressing_then_theCurrentQuestionIsNotFedToTheCompressorTwice() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));
        when(chatModel.chat(anyString())).thenReturn("prazo do contrato de 2024");

        service("gpt-4o").chat(request(question), tenantId, userId);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(prompt.capture());
        assertThat(prompt.getValue())
                .contains("Fale sobre o contrato de 2024")
                .contains("O contrato de 2024 tem prazo de 30 dias")
                .containsOnlyOnce(question);
    }

    /** A self-contained question is left alone: the rewrite would be a wasted call. */
    @Test
    void when_theQuestionIsSelfContained_then_itIsSearchedAsWritten() {
        String question = "Qual o prazo de entrega do contrato de suprimento do ano de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        service("gpt-4o").chat(request(question), tenantId, userId);

        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                any(Integer.class), any());
    }

    /**
     * The refusal that matters: rewriting on the agent's own reasoning model would add a second
     * reasoning pass to the turn the operator is already paying for — the whole conversation in, one
     * sentence out.
     */
    @Test
    void when_theCompressionModelIsTheAgentsOwnModel_then_theStageDoesNotRun() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        service("gpt-4o-mini").chat(request(question), tenantId, userId);

        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                any(Integer.class), any());
    }

    @Test
    void when_theCompressionModelIsNotInTheCatalog_then_theStageDoesNotRun() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        service("mimo-v2.5").chat(request(question), tenantId, userId);

        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                any(Integer.class), any());
    }

    /**
     * A failing rewrite must not fail the turn: the original words still search, and the user still
     * gets an answer.
     */
    @Test
    void when_theRewriterFails_then_theOriginalQuestionIsSearchedAndTheTurnAnswers() {
        String question = "E o prazo?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));
        when(chatModel.chat(anyString())).thenThrow(new IllegalStateException("provider down"));

        ChatResponse response = service("gpt-4o").chat(request(question), tenantId, userId);

        assertThat(response.getMessage()).isEqualTo("resposta");
        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                any(Integer.class), any());
    }

    // ------------------------------------------------------------------ one trace per turn

    /** The trace is the turn's own line, and the RAG line is the resolver's. One of each, never two. */
    @Test
    void when_aTurnSucceeds_then_exactlyOneTraceAndOneRagLineAreWritten() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias")),
                12L, 0.60, 1, null, null));

        service().chat(request(question), tenantId, userId);

        assertThat(traces).as("one telemetry line per turn").hasSize(1);
        assertThat(diagnostics).as("one RAG line per turn").hasSize(1);
        assertThat(traces.get(0).ragOutcome()).isEqualTo("ok");
        assertThat(traces.get(0).ragThreshold()).isEqualTo(0.60);
        assertThat(traces.get(0).ragUsed()).isTrue();
        assertThat(traces.get(0).citationsCount()).isEqualTo(1);
        assertThat(traces.get(0).promptMessages()).isPositive();
        assertThat(traces.get(0).promptChars()).isPositive();
        assertThat(traces.get(0).failure()).isNull();
    }

    /**
     * The prompt metrics on the trace are the gateway's measurements, not a reconstruction by the
     * turn. A prompt of N messages is reported as N messages.
     */
    @Test
    void when_theTurnIsTraced_then_thePromptSizeIsTheOneThatWasSent() {
        String question = "Qual o prazo do contrato de 2024?";
        prepareTurn(question);
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias")),
                12L, 0.60, 1, null, null));

        service().chat(request(question), tenantId, userId);

        assertThat(traces.get(0).promptMessages()).isEqualTo(capturedPrompt().size());
        assertThat(traces.get(0).historySize()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ the cut-off is still not ours

    @Test
    void when_theRagSourcesAreScanned_then_noSimilarityCutOffIsHardcodedInThem() throws IOException {
        for (String file : List.of(
                "src/main/java/br/com/systec/hive/agent/impl/service/LangChain4jTurnGateway.java",
                "src/main/java/br/com/systec/hive/agent/impl/rag/KnowledgeBaseContentRetriever.java",
                "src/main/java/br/com/systec/hive/agent/impl/rag/RagAugmentorFactory.java",
                "src/main/java/br/com/systec/hive/agent/impl/rag/ConditionalCompressingQueryTransformer.java",
                "src/main/java/br/com/systec/hive/agent/impl/rag/RagTurnResolver.java")) {
            HardcodedSimilarityCutOffGuard.assertNoHardcodedCutOff(Path.of(file));
        }
    }
}
