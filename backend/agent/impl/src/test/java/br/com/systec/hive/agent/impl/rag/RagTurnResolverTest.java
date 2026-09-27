package br.com.systec.hive.agent.impl.rag;

import br.com.systec.hive.agent.api.domain.RagContext;
import br.com.systec.hive.agent.impl.service.CredentialResolver;
import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.domain.RagQuerySettings;
import br.com.systec.hive.knowledge.api.domain.RagRetrievalResult;
import br.com.systec.hive.knowledge.api.exceptions.KbDimensionMismatchException;
import br.com.systec.hive.knowledge.api.port.KbAgentLinkPort;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The RAG resolution of one turn, on its own.
 *
 * <p>This class exists because these paths were unreachable without a whole chat turn. Every one of
 * them used to be asserted by constructing {@code ChatServiceImpl} with eleven collaborators, a rate
 * limiter, a session store and a message store, and then stubbing a provider in order to observe a
 * link-lookup failure. The invariants below — one diagnostic per turn, no reason invented, the
 * retrieval's own facts carried rather than restated — are the ones that were expensive to check, and
 * they are now cheap enough that there is no excuse for not checking them.
 */
@ExtendWith(MockitoExtension.class)
class RagTurnResolverTest {

    @Mock
    private KbRetrievalPort kbRetrieval;
    @Mock
    private KbAgentLinkPort kbLinkPort;
    @Mock
    private LlmClientFactory<ChatModel> llmClientFactory;
    @Mock
    private ChatModel chatModel;

    private final List<RagDiagnostic> diagnostics = new ArrayList<>();

    private UUID tenantId;
    private UUID agentId;
    private UUID sessionId;
    private UUID kbId;
    private String question;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        agentId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        question = "Qual o prazo do contrato de 2024?";
    }

    private RagTurnResolver resolver(String compressionModel) {
        return new RagTurnResolver(kbRetrieval, kbLinkPort, diagnostics::add, llmClientFactory,
                compressionModel, 40);
    }

    private RagTurnResolver.RagRequest request() {
        return new RagTurnResolver.RagRequest(tenantId, agentId, sessionId, question,
                AiProvider.OPENAI, "gpt-4o-mini",
                new CredentialResolver.ResolvedCredential("sk-test", null),
                conversationWith(question));
    }

    /** The turn's own question is in the saved history: it is saved before the RAG resolution. */
    private List<ChatMessage> conversationWith(String lastQuestion) {
        return List.of(new UserMessage("Fale sobre o contrato de 2024"),
                new AiMessage("O contrato de 2024 tem prazo de 30 dias"),
                new UserMessage(lastQuestion));
    }

    private void linkKb() {
        when(kbLinkPort.linkedKbIds(agentId, tenantId)).thenReturn(List.of(kbId));
    }

    private void stubRetrieval(RagRetrievalResult result) {
        linkKb();
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), anyList(), any(), anyInt(), any()))
                .thenReturn(result);
    }

    private KbChunk chunk(String text, double score) {
        KbChunk chunk = new KbChunk();
        chunk.setDocId(UUID.randomUUID());
        chunk.setDocName("contrato-2024.pdf");
        chunk.setChunkId(UUID.randomUUID());
        chunk.setText(text);
        chunk.setScore(score);
        return chunk;
    }

    private RagDiagnostic onlyDiagnostic() {
        assertThat(diagnostics)
                .as("exactly one RAG line per turn, whatever the path")
                .hasSize(1);
        return diagnostics.get(0);
    }

    // ------------------------------------------------------------------ the happy path

    @Test
    void when_theRetrievalFindsText_then_theContextIsInjectedWithItsOwnCitations() {
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("prazo de 30 dias", 0.91), chunk("multa de 2%", 0.77)),
                42L, 0.60, 1, null, null));

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isTrue();
        assertThat(context.outcome()).isEqualTo(RagOutcome.OK);
        assertThat(context.threshold()).isEqualTo(0.60);
        assertThat(context.injectedChars()).isPositive();
        assertThat(context.injected()).isNotNull();
        assertThat(context.citations())
                .as("a citation names what the model was given, not what the search returned")
                .hasSize(2)
                .allSatisfy(citation -> assertThat(citation.getDocName()).isEqualTo("contrato-2024.pdf"));
    }

    /** The cut-off is stated as a policy, so the number that ran can only be the base model's. */
    @Test
    void when_theTurnSearches_then_itDelegatesTheCutOffToTheBaseModel() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.NO_CHUNK_ABOVE_THRESHOLD, 5L, 0.60, 1,
                "vector search returned no chunk above the similarity threshold", null));

        resolver(null).resolve(request());

        ArgumentCaptor<RagQuerySettings> policy = ArgumentCaptor.forClass(RagQuerySettings.class);
        verify(kbRetrieval).retrieveWithOutcome(eq(tenantId), eq(List.of(kbId)), eq(question),
                eq(RagTurnResolver.RAG_MAX_RESULTS), policy.capture());
        assertThat(policy.getValue())
                .as("an explicit number here would be a cut-off this module cannot know")
                .isEqualTo(RagQuerySettings.fromKbConvention());
        assertThat(policy.getValue().isFromKbConvention()).isTrue();
    }

    // ------------------------------------------------------------------ every path is reported

    @Test
    void when_theKnowledgeModuleIsNotWired_then_itSaysSoAndInjectsNothing() {
        RagTurnResolver resolver = new RagTurnResolver(null, null, diagnostics::add, llmClientFactory,
                null, 40);

        RagContext context = resolver.resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.RAG_DISABLED);
        assertThat(onlyDiagnostic().reason()).isEqualTo("knowledge module not wired");
        assertThat(onlyDiagnostic().agentId()).isEqualTo(agentId);
        assertThat(onlyDiagnostic().tenantId()).isEqualTo(tenantId);
        verify(kbRetrieval, never()).retrieveWithOutcome(any(), any(), any(), anyInt(), any());
    }

    @Test
    void when_theQuestionIsBlank_then_itIsReportedAsNoLinkAndNothingIsSearched() {
        RagContext context = resolver(null)
                .resolve(new RagTurnResolver.RagRequest(tenantId, agentId, sessionId, "   ",
                        AiProvider.OPENAI, "gpt-4o-mini", null, List.of()));

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.NO_LINK);
        assertThat(onlyDiagnostic().reason()).isEqualTo("empty question");
        verify(kbRetrieval, never()).retrieveWithOutcome(any(), any(), any(), anyInt(), any());
    }

    @Test
    void when_theAgentHasNoBaseLinked_then_itSaysNoLinkAndSearchesNothing() {
        when(kbLinkPort.linkedKbIds(agentId, tenantId)).thenReturn(List.of());

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.NO_LINK);
        assertThat(onlyDiagnostic().kbIds()).isEmpty();
        assertThat(onlyDiagnostic().reason()).isEqualTo("agent has no knowledge base linked");
        verify(kbRetrieval, never()).retrieveWithOutcome(any(), any(), any(), anyInt(), any());
    }

    @Test
    void when_theLinkLookupFails_then_theTurnStillResolvesAndTheCauseIsClassified() {
        when(kbLinkPort.linkedKbIds(agentId, tenantId))
                .thenThrow(new IllegalStateException("link table down for agent=" + agentId));

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.ERROR);
        RagDiagnostic diagnostic = onlyDiagnostic();
        assertThat(diagnostic.reason()).isEqualTo("agent->knowledge base link lookup failed");
        assertThat(diagnostic.cause())
                .as("class + closed code only: the message is where the ids would leak")
                .isEqualTo("IllegalStateException:unexpected")
                .doesNotContain(agentId.toString());
    }

    @Test
    void when_theRetrievalFails_then_itIsReportedAndNothingIsInjected() {
        linkKb();
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), anyList(), any(), anyInt(), any()))
                .thenThrow(new IllegalStateException("retrieval failed for query=[PERGUNTA SIGILOSA]"));

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.ERROR);
        RagDiagnostic diagnostic = onlyDiagnostic();
        assertThat(diagnostic.kbIds()).containsExactly(kbId);
        assertThat(diagnostic.reason()).isEqualTo("knowledge retrieval failed");
        assertThat(diagnostic.cause()).doesNotContain("PERGUNTA SIGILOSA");
    }

    /**
     * The port returning nothing at all is a different failure from the port returning an empty
     * result, and conflating them would put a word in the diagnostic's mouth that nobody reported.
     */
    @Test
    void when_theRetrievalPortReturnsNothingAtAll_then_itIsReportedAsItsOwnFailure() {
        linkKb();
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), anyList(), any(), anyInt(), any()))
                .thenReturn(null);

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.outcome()).isEqualTo(RagOutcome.ERROR);
        assertThat(onlyDiagnostic().reason()).isEqualTo("retrieval port returned no result");
    }

    /**
     * The one outcome the retrieval throws instead of returning. No result is ever produced for it, so
     * the line is built here — and it has to carry the base count from the exception, because
     * reporting {@code foundKbs=0} for two bases that were both found sends the reader to the link
     * table instead of to the dimensions. That false arrow is the same shape as the bug that started
     * this reporting.
     */
    @Test
    void when_theBasesDisagreeOnDimension_then_theTurnLineCarriesTheCountFromTheException() {
        linkKb();
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), anyList(), any(), anyInt(), any()))
                .thenThrow(new KbDimensionMismatchException(3));

        assertThatThrownBy(() -> resolver(null).resolve(request()))
                .isInstanceOf(KbDimensionMismatchException.class);

        RagDiagnostic diagnostic = onlyDiagnostic();
        assertThat(diagnostic.outcome()).isEqualTo(RagOutcome.DIMENSION_MISMATCH);
        assertThat(diagnostic.foundKbs())
                .as("three bases were found and checked; reporting 0 sends the reader to the link table")
                .isEqualTo(3);
        assertThat(diagnostic.kbIds()).containsExactly(kbId);
        assertThat(diagnostic.reason()).isNotBlank();
        assertThat(diagnostic.cause()).isEqualTo("KbDimensionMismatchException:dimension_mismatch");
    }

    // ------------------------------------------------------------------ nothing invented

    /**
     * The two lines a turn emits — the retrieval's and the agent's — used to disagree, and the agent's
     * was the wrong one: a {@code no_chunk_above_threshold} whose second line claimed
     * {@code foundKbs=0, reason="empty retrieval"}, i.e. that the bases were never found. An operator
     * reading it goes and looks at the link table, and the real cause — a cut-off that rejected every
     * chunk — stays untouched.
     *
     * <p>Characterisation, not a string match: for every outcome the retrieval can hand <b>back</b>, the
     * facts it reported have to arrive unchanged on the turn's line. The turn adds {@code agentId} and
     * the size of the context block; it may not restate anything.
     *
     * <p>Two enum values are deliberately absent, because the production retrieval never returns
     * them: {@code DIMENSION_MISMATCH} is thrown (covered above) and {@code RAG_DISABLED} is raised
     * here when the knowledge module is not wired (also covered above). Both take a different path
     * into the recorder, and neither is worth fabricating a result for.
     */
    @Test
    void when_everyReturnedOutcomeReachesTheTurnLine_then_itSaysWhatTheRetrievalSaid() {
        for (RagOutcome outcome : List.of(RagOutcome.OK, RagOutcome.NO_LINK,
                RagOutcome.KB_NOT_FOUND_FOR_TENANT, RagOutcome.NO_CHUNK_ABOVE_THRESHOLD,
                RagOutcome.TIMEOUT, RagOutcome.ERROR)) {
            diagnostics.clear();
            RagRetrievalResult result = RagRetrievalResult.of(outcome, List.of(), 7L, 0.60, 3,
                    "reason for " + outcome.wireName(), "Cause:timeout");

            stubRetrieval(result);
            resolver(null).resolve(request());

            RagDiagnostic turn = onlyDiagnostic();
            assertThat(turn.outcome()).as("%s outcome", outcome).isEqualTo(outcome);
            assertThat(turn.foundKbs()).as("%s foundKbs", outcome).isEqualTo(result.foundKbs()).isEqualTo(3);
            assertThat(turn.reason()).as("%s reason", outcome).isEqualTo(result.reason());
            assertThat(turn.cause()).as("%s cause", outcome).isEqualTo(result.cause());
            assertThat(turn.threshold()).as("%s threshold", outcome).isEqualTo(result.threshold());
            assertThat(turn.chunks()).as("%s chunks", outcome).isEqualTo(result.chunkCount());
            assertThat(turn.kbIds()).as("%s kbIds", outcome).containsExactly(kbId);
            assertThat(turn.agentId()).as("%s agentId", outcome).isEqualTo(agentId);
            assertThat(turn.tenantId()).as("%s tenantId", outcome).isEqualTo(tenantId);
        }
    }

    /** A retrieval that resolved no cut-off has none to report, and this class has none to invent. */
    @Test
    void when_theRetrievalResolvedNoCutOff_then_theTurnLineReportsNone() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.KB_NOT_FOUND_FOR_TENANT, 4L, null, 0,
                "linked knowledge bases do not exist for this tenant", null));

        resolver(null).resolve(request());

        assertThat(onlyDiagnostic().threshold())
                .as("reporting a constant is how a line explains one threshold while another ran")
                .isNull();
    }

    /** A successful retrieval reports nothing it failed at, so the turn must not invent a reason. */
    @Test
    void when_theRetrievalSucceeded_then_theTurnLineCarriesNoReasonOrCause() {
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias", 0.91)),
                3L, 0.60, 1, null, null));

        resolver(null).resolve(request());

        RagDiagnostic turn = onlyDiagnostic();
        assertThat(turn.reason()).isNull();
        assertThat(turn.cause()).isNull();
        assertThat(turn.chunks()).isEqualTo(1);
        assertThat(turn.foundKbs()).isEqualTo(1);
        assertThat(turn.systemBlockChars()).isPositive();
    }

    /**
     * The latency is the one field the two lines legitimately differ on: the retrieval measures the
     * vector search, the turn measures the whole RAG resolution. So the turn line reports the turn's
     * own time and not the search's.
     */
    @Test
    void when_theRetrievalIsReportedOnTheTurnLine_then_theLatencyIsTheTurnsNotTheSearchs() {
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo de 30 dias", 0.91)),
                9_999L, 0.60, 1, null, null));

        resolver(null).resolve(request());

        assertThat(onlyDiagnostic().elapsedMs())
                .isNotNegative()
                .isNotEqualTo(9_999L);
    }

    // ------------------------------------------------------------------ the empty-content guard

    /**
     * A retrieval whose chunks are all textless satisfies {@code hasChunks()}, and the retriever cannot
     * offer any of them to the pipeline — the official {@code TextSegment} refuses blank text. The
     * augmentor then answers an empty list by handing back the message it was given, which is the
     * question. Without the guard the turn would report the length of the question as the size of the
     * context block, and would claim context it never received.
     */
    @Test
    void when_noChunkCarriesText_then_nothingIsInjectedAndTheOutcomeIsTheRetrievalsOwn() {
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("", 0.9), chunk("   ", 0.8), chunk(null, 0.7)),
                9L, 0.60, 1, null, null));

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isFalse();
        assertThat(context.injected()).isNull();
        assertThat(context.injectedChars()).isZero();
        assertThat(context.citations()).isEmpty();
        assertThat(context.outcome()).isEqualTo(RagOutcome.OK);
        RagDiagnostic turn = onlyDiagnostic();
        assertThat(turn.systemBlockChars())
                .as("no block was injected, so the size reported must be the absence of one")
                .isEqualTo(RagDiagnostic.NO_SYSTEM_BLOCK);
        assertThat(turn.chunks())
                .as("the diagnostic keeps describing the search as it was")
                .isEqualTo(3);
    }

    /**
     * One chunk dropped for having no text is the case that separates the two lists: the search found
     * two, the model was given one, and the citation list may only name the one.
     */
    @Test
    void when_oneChunkHasNoText_then_onlyTheInjectedOneIsCited() {
        stubRetrieval(RagRetrievalResult.of(RagOutcome.OK,
                List.of(chunk("prazo de 30 dias", 0.9), chunk("", 0.8)),
                9L, 0.60, 1, null, null));

        RagContext context = resolver(null).resolve(request());

        assertThat(context.used()).isTrue();
        assertThat(context.citations()).hasSize(1);
        assertThat(onlyDiagnostic().chunks()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ resilience

    /** A diagnostics sink must never break retrieval — it is observability, not the flow. */
    @Test
    void when_theRecorderFails_then_theContextIsStillResolved() {
        linkKb();
        when(kbRetrieval.retrieveWithOutcome(eq(tenantId), anyList(), any(), anyInt(), any()))
                .thenReturn(RagRetrievalResult.of(RagOutcome.OK, List.of(chunk("prazo", 0.9)),
                        3L, 0.60, 1, null, null));
        RagTurnResolver resolver = new RagTurnResolver(kbRetrieval, kbLinkPort,
                diagnostic -> {
                    throw new IllegalStateException("rag trace down");
                }, llmClientFactory, null, 40);

        RagContext context = resolver.resolve(request());

        assertThat(context.used()).isTrue();
        assertThat(context.citations()).hasSize(1);
    }

    /**
     * The question is fed to the compressor as the query and stripped from its memory, so a follow-up
     * rewrite is not billed twice for the same sentence.
     */
    @Test
    void when_compressing_then_theCurrentQuestionIsNotInTheMemoryToo() {
        stubAllowedCompression();

        resolver("gpt-4o").resolve(request());

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chatModel).chat(prompt.capture());
        assertThat(prompt.getValue())
                .contains("Fale sobre o contrato de 2024")
                .contains("O contrato de 2024 tem prazo de 30 dias")
                .containsOnlyOnce(question);
    }

    // ------------------------------------------------------- the compressor's three refusals

    private void stubAllowedCompression() {
        when(llmClientFactory.createClient(eq(AiProvider.OPENAI), eq("gpt-4o"), eq(0.0),
                eq(RagTurnResolver.COMPRESSION_MAX_TOKENS), eq(null), eq("sk-test"), eq(null)))
                .thenReturn(chatModel);
        when(chatModel.chat(any(String.class))).thenReturn("Qual o prazo do contrato de 2024?");
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));
    }

    /**
     * The other side of the same guard, and the half that costs money: with the stage on and a
     * legitimate model, exactly <b>one</b> client is built.
     *
     * <p>Stated first and on purpose. The three refusals below all assert that no client was built,
     * and a resolver that simply never built one would satisfy every one of them — so without this
     * test they would pass against a compressor that had stopped working entirely.
     */
    @Test
    void when_theCompressorIsOnWithALegitimateModel_then_exactlyOneClientIsBuilt() {
        stubAllowedCompression();

        resolver("gpt-4o").resolve(request());

        verify(llmClientFactory, times(1)).createClient(eq(AiProvider.OPENAI), eq("gpt-4o"), eq(0.0),
                eq(RagTurnResolver.COMPRESSION_MAX_TOKENS), eq(null), eq("sk-test"), eq(null));
        verify(chatModel).chat(any(String.class));
    }

    /**
     * Off unless an operator names a model. The stage spends one LLM round-trip on every question that
     * looks like a follow-up, and an unmeasured extra call is exactly what the latency baseline
     * forbids — so "not configured" has to be decided <b>before</b> a client is built, not after one
     * was built and found unnecessary.
     */
    @Test
    void when_noCompressionModelIsConfigured_then_noClientIsEverBuilt() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        resolver(null).resolve(request());

        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    /** A blank name is the same absence written differently, and has to be read as the same absence. */
    @Test
    void when_theCompressionModelIsBlank_then_noClientIsEverBuilt() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        resolver("   ").resolve(request());

        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * The refusal that costs the most if it ever regresses: rewriting on the agent's own reasoning
     * model adds a second reasoning pass to the turn the operator is already paying for — the whole
     * conversation in, one sentence out — on the most expensive model in the catalogue.
     */
    @Test
    void when_theCompressionModelIsTheAgentsOwnModel_then_noClientIsEverBuilt() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        // The request names gpt-4o-mini as the agent's own model, so that is the configured one.
        resolver("gpt-4o-mini").resolve(request());

        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Not in the vendor's catalogue: the call would fail at request time, and failing the turn over a
     * rewrite is strictly worse than searching with the original words.
     */
    @Test
    void when_theCompressionModelIsNotInTheCatalog_then_noClientIsEverBuilt() {
        stubRetrieval(RagRetrievalResult.empty(RagOutcome.OK, 5L, 0.60, 1, null, null));

        resolver("mimo-v2.5").resolve(request());

        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }
}
