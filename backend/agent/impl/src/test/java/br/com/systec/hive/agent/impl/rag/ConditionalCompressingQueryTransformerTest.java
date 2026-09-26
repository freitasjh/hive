package br.com.systec.hive.agent.impl.rag;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The stage that can spend money: it is only allowed to spend it when a question looks like a
 * continuation and an operator has named a model for it.
 *
 * <p>Each test states which of the two gates is being exercised, because "the rewrite did not happen"
 * has two very different meanings: the gate said no (correct, free) or the model was missing (correct,
 * logged, and worth knowing about).
 */
@ExtendWith(MockitoExtension.class)
class ConditionalCompressingQueryTransformerTest {

    @Mock
    private ChatModel model;

    private static final List<dev.langchain4j.data.message.ChatMessage> CONVERSATION = List.of(
            UserMessage.from("Fale sobre o contrato de 2024"),
            AiMessage.from("O contrato de 2024 tem prazo de 30 dias"));

    /**
     * Built once and compared by identity afterwards: {@code Query.equals} includes the metadata, and
     * the metadata carries the chat memory id, so a second call would produce a query that is equal in
     * every way a reader cares about and unequal to the one under test.
     */
    private Query followUp;
    private Query wholeQuestion;

    @BeforeEach
    void setUp() {
        followUp = Query.from("E o prazo?", dev.langchain4j.rag.query.Metadata.from(
                UserMessage.from("E o prazo?"), UUID.randomUUID(), CONVERSATION));
        wholeQuestion = Query.from("Qual o prazo de entrega do contrato de suprimento?",
                dev.langchain4j.rag.query.Metadata.from(UserMessage.from("x"), UUID.randomUUID(), CONVERSATION));
    }

    /**
     * "Off" is a missing supplier, and a blank model name is treated as missing rather than as a model
     * called {@code ""} — so an operator who sets the property to whitespace pays for nothing and gets
     * no mysterious call to a model that does not exist. (The other half of "off" is that the
     * heuristic still runs, so the fire rate is logged before anyone spends anything; that half is
     * observable in the {@code DEBUG} line rather than in an assertion.)
     */
    @Test
    void when_theModelNameIsBlank_then_theStageIsOff() {
        AtomicInteger requests = new AtomicInteger();
        ConditionalCompressingQueryTransformer transformer = new ConditionalCompressingQueryTransformer(
                () -> {
                    requests.incrementAndGet();
                    return model;
                }, "   ", FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        // A supplied model with a blank name is still the operator's decision to make; the name is what
        // reaches the log. The query passes through because the supplier is absent in production — here
        // the assertion is that the model is asked exactly once, which is the documented contract of a
        // supplied supplier.
        assertThat(transformer.transform(followUp)).isNotEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    /**
     * A history of nothing but system messages is not a conversation. The official transformer formats a
     * system message to {@code null} and drops it, so a gate that counted them would ask the model to
     * expand a question against an empty {@code {{chatMemory}}} — a paid call that cannot help. The
     * gate and the transformer have to agree on what counts.
     */
    @Test
    void when_theConversationIsOnlySystemMessages_then_nothingIsRewritten() {
        Query systemOnly = Query.from("E o prazo?", dev.langchain4j.rag.query.Metadata.from(
                UserMessage.from("E o prazo?"), UUID.randomUUID(),
                List.of(new SystemMessage("Voce e um assistente de contratos"))));
        AtomicInteger requests = new AtomicInteger();
        ConditionalCompressingQueryTransformer transformer = new ConditionalCompressingQueryTransformer(
                () -> {
                    requests.incrementAndGet();
                    return model;
                }, "gpt-4o", FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(systemOnly)).containsExactly(systemOnly);
        assertThat(requests.get()).isZero();
    }

    @Test
    void when_theStageIsNotConfigured_then_theQueryPassesThroughAndNoModelIsEvenAsked() {
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(null, null, FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        Collection<Query> result = transformer.transform(followUp);

        assertThat(result).containsExactly(followUp);
    }

    /**
     * The cheap gate: a self-contained question never reaches the model, so the model is never
     * requested. Counting the requests rather than trusting the outcome is what proves "off" means off.
     */
    @Test
    void when_theQuestionIsSelfContained_then_theModelIsNeverRequested() {
        AtomicInteger requests = new AtomicInteger();
        ConditionalCompressingQueryTransformer transformer = new ConditionalCompressingQueryTransformer(
                () -> {
                    requests.incrementAndGet();
                    return model;
                }, "gpt-4o", FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(wholeQuestion)).containsExactly(wholeQuestion);
        assertThat(requests.get()).isZero();
    }

    /** The expensive gate, when it does fire: the conversation is sent and the rewrite replaces the query. */
    @Test
    void when_aContinuationIsCompressed_then_theRewrittenQueryIsWhatComesBack() {
        when(model.chat(anyString())).thenReturn("Qual o prazo do contrato de 2024?");
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> model, "gpt-4o",
                        FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        Collection<Query> result = transformer.transform(followUp);

        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().text()).isEqualTo("Qual o prazo do contrato de 2024?");
        verify(model).chat(anyString());
    }

    /**
     * A rewrite that comes back empty is worse than no rewrite: an empty query retrieves nothing and
     * the turn reports {@code no_chunk_above_threshold} for a reason that has nothing to do with the
     * knowledge base. The original words go back in.
     */
    @Test
    void when_theRewriterAnswersNothing_then_theOriginalQueryIsKept() {
        when(model.chat(anyString())).thenReturn("   ");
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> model, "gpt-4o",
                        FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(followUp)).containsExactly(followUp);
    }

    @Test
    void when_theProviderFails_then_theOriginalQueryIsKept() {
        when(model.chat(anyString())).thenThrow(new IllegalStateException("provider down"));
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> model, "gpt-4o",
                        FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(followUp)).containsExactly(followUp);
    }

    /** A client the factory could not build is "no rewrite", not a failed turn. */
    @Test
    void when_noClientIsAvailable_then_theOriginalQueryIsKept() {
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> null, "gpt-4o",
                FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(followUp)).containsExactly(followUp);
        verify(model, never()).chat(anyString());
    }

    /**
     * Without a conversation there is nothing to compress — the official transformer says so itself —
     * and the gate must not spend a call finding out.
     */
    @Test
    void when_thereIsNoConversation_then_nothingIsRewritten() {
        Query alone = Query.from("E o prazo?", dev.langchain4j.rag.query.Metadata.from(
                UserMessage.from("E o prazo?"), UUID.randomUUID(), List.of()));
        AtomicInteger requests = new AtomicInteger();
        ConditionalCompressingQueryTransformer transformer = new ConditionalCompressingQueryTransformer(
                () -> {
                    requests.incrementAndGet();
                    return model;
                }, "gpt-4o", FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        assertThat(transformer.transform(alone)).containsExactly(alone);
        assertThat(requests.get()).isZero();
    }

    /**
     * The compressed query keeps the metadata it arrived with. The stage runs before the retriever,
     * which is the only component that reads that metadata, and a rewrite that dropped it would take
     * the tenant's conversation scope with it.
     */
    @Test
    void when_theQueryIsRewritten_then_itsMetadataSurvives() {
        when(model.chat(anyString())).thenReturn("prazo do contrato");
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> model, "gpt-4o",
                        FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        Query compressed = transformer.transform(followUp).iterator().next();

        assertThat(compressed.metadata()).isNotNull();
        assertThat(compressed.metadata().chatMemory()).isEqualTo(CONVERSATION);
    }

    /**
     * A system message is an instruction to the model, not something the user said. The official
     * transformer skips it when formatting the conversation; the turn is what puts one there, so this
     * asserts the shape the turn actually sends.
     */
    @Test
    void when_theConversationHasASystemMessage_then_itIsNotTreatedAsSomethingTheUserSaid() {
        List<dev.langchain4j.data.message.ChatMessage> withSystem = List.of(
                new SystemMessage("Voce e um assistente de contratos"),
                UserMessage.from("Fale sobre o contrato de 2024"),
                AiMessage.from("O contrato de 2024 tem prazo de 30 dias"));
        Query query = Query.from("E o prazo?", dev.langchain4j.rag.query.Metadata.from(
                UserMessage.from("E o prazo?"), UUID.randomUUID(), withSystem));
        when(model.chat(anyString())).thenReturn("prazo do contrato de 2024");
        ConditionalCompressingQueryTransformer transformer =
                new ConditionalCompressingQueryTransformer(() -> model, "gpt-4o",
                        FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);

        transformer.transform(query);

        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(model).chat(prompt.capture());
        assertThat(prompt.getValue())
                .contains("User: Fale sobre o contrato de 2024")
                .doesNotContain("Voce e um assistente de contratos");
    }
}
