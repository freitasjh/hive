package br.com.systec.hive.agent.impl.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate that decides whether a question is rewritten with the conversation.
 *
 * <p>The last test in this class is the one that matters: it is a labelled set, and it fails with the
 * confusion matrix in the message. A heuristic that cannot be held to account in a test is a reason
 * to spend an LLM call for nothing, and the first version of this rule (a 60-character limit) looked
 * reasonable and fired on three quarters of the self-contained questions in that set.
 */
class FollowUpQueryHeuristicTest {

    // ---------------------------------------------------------------- each rule, alone

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "E o prazo do contrato?            | leading_connective:e",
            "e o prazo do contrato?            | leading_connective:e",
            "E quando ele vence?              | leading_connective:e quando",
            "Então?                            | leading_connective:então",
            "entao?                            | leading_connective:entao",
            "Por que ele sumiu?               | leading_connective:por que",
            "Qual o melhor modelo?            | leading_connective:qual o melhor",
            "Além disso ele muda?             | leading_connective:além disso",
            "Mas ele vale?                    | leading_connective:mas",
    })
    void when_questionOpensWithAConnective_then_itIsAContinuation(String question, String expectedReason) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress()).isTrue();
        assertThat(decision.reason()).isEqualTo(expectedReason);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "Qual o prazo dele?",
            "Isso se aplica ao anexo?",
            "Aquilo ainda vale?",
            "O mesmo dia vale para a multa?",
            "A outra proposta foirenewed?",
            "Qual delas é a melhor?",
    })
    void when_questionCarriesAnUnresolvedReference_then_itIsAContinuation(String question) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress()).isTrue();
        assertThat(decision.reason()).startsWith("indefinite_reference:");
    }

    /**
     * Word-anchored, so the reference is a word and not a fragment of one. Without this, "modelo"
     * reads as a reference to "ele" and every question about models pays for a rewrite.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "Qual o modelo de embedding funciona melhor para documentos em português?",
            "Qual o paralelismo de elite na arquitetura atual do motor de busca?",
    })
    void when_theReferenceIsInsideALongerWord_then_itIsNotAContinuation(String question) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress())
                .as("\"modelo\" and \"elite\" contain the letters of \"ele\" and must not count")
                .isFalse();
        assertThat(decision.reason()).isEqualTo("no_signal");
    }

    /**
     * The length rule on its own: questions with no connective and no reference, which is the only
     * thing left to catch them.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"Preço?", "Contrato 2025?", "Quanto tempo?", "E-mail?", "Multa?"})
    void when_questionIsTooShortToStandAlone_then_itIsAContinuation(String question) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress()).isTrue();
        assertThat(decision.reason()).isEqualTo("elliptical<=" + FollowUpQueryHeuristic.ELLIPTICAL_LIMIT);
    }

    /**
     * A question that is both elliptical and a connective reports the connective, because that is the
     * more specific rule and the log line should name the one an operator can act on.
     */
    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "E?       | leading_connective:e",
            "Por que? | leading_connective:por que",
    })
    void when_anEllipticalQuestionAlsoOpensWithAConnective_then_theSpecificRuleIsReported(
            String question, String expectedReason) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress()).isTrue();
        assertThat(decision.reason()).isEqualTo(expectedReason);
    }

    // ---------------------------------------------------------------- the cases that must not fire

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {
            "Qual o prazo de entrega do contrato de suprimento do ano de 2024?",
            "Explique a diferença entre embeddings e reranking em português.",
            "Como funciona o chunking hierárquico com tokenização por sentenças?",
            "Quais documentos precisam ser assinados para abrir um CNPJ?",
            "Quanto custa a assinatura mensal do plano?",
            "Quais são os campos da entidade KnowledgeBase no banco?",
    })
    void when_questionIsSelfContained_then_nothingIsRewritten(String question) {
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, true);

        assertThat(decision.compress()).isFalse();
        assertThat(decision.reason()).isEqualTo("no_signal");
    }

    /**
     * A first question has no conversation to be continued, so no signal can make it one — and the
     * reason says so, because "no_signal" on a first question would send the reader looking for a
     * wording problem that is not there.
     */
    @Test
    void when_thereIsNoHistory_then_nothingIsRewrittenWhateverTheWording() {
        for (String question : List.of("E o prazo?", "Isso vale?", "Qual o prazo?")) {
            FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide(question, false);

            assertThat(decision.compress()).as("%s", question).isFalse();
            assertThat(decision.reason()).as("%s", question).isEqualTo("no_history");
        }
    }

    /**
     * The limit is configurable, so a test that could only exercise the shipped number would not notice
     * the parameter being ignored.
     */
    @Test
    void when_theEllipticalLimitIsConfigured_then_theLengthRuleFollowsIt() {
        // 36 characters as typed; the rule reads the normalised form, which drops the question mark,
        // so the two limits around the decision are its length and its length minus two.
        String question = "Qual o prazo de entrega do contrato?";

        assertThat(FollowUpQueryHeuristic.decide(question, true, question.length()).compress())
                .as("a limit at or above the question length makes it elliptical")
                .isTrue();
        assertThat(FollowUpQueryHeuristic.decide(question, true, question.length() - 2).compress())
                .as("and one character below the normalised length does not")
                .isFalse();
    }

    @Test
    void when_questionIsEmpty_then_nothingIsRewritten() {
        assertThat(FollowUpQueryHeuristic.decide(null, true).reason()).isEqualTo("empty_question");
        assertThat(FollowUpQueryHeuristic.decide("   ", true).reason()).isEqualTo("empty_question");
    }

    @Test
    void when_questionIsPaddedOrMixedCase_then_itIsStillRead() {
        // "  E o Prazo?  " and "e o prazo?" are the same question; a heuristic that normalises only
        // half of that misses a third of real typing.
        FollowUpQueryHeuristic.Decision decision = FollowUpQueryHeuristic.decide("  E O Prazo?  ", true);

        assertThat(decision.compress()).isTrue();
    }

    // ---------------------------------------------------------------- the measurement

    /**
     * The labelled set, and the check that keeps the rules honest.
     *
     * <p>Every row is a question someone can plausibly type, half of them continuations of the
     * conversation in {@link #CONVERSATION} and half of them complete questions. The assertions are the
     * confusion matrix rather than a per-row expectation, so a change that trades one for the other
     * shows up as a number with both sides in the failure message.
     *
     * <p>24 hand-labelled questions is a small sample and this test says so: it proves the rules hold
     * on a set someone checked, not that they generalise. The production number comes from the
     * {@code query compression applied reason=…} log line, and this is the baseline to compare it to.
     */
    @Test
    void when_theLabelledSetIsScored_then_theRulesSeparateContinuationsFromWholeQuestions() {
        List<String> continuations = List.of(
                "E o prazo do contrato de 2024?",
                "E ele?",
                "E a multa por atraso?",
                "Então?",
                "Por que ele foi rejeitado?",
                "Qual o melhor contrato?",
                "Isso vale para o anexo?",
                "O mesmo prazo vale para o contrato?",
                "e a outra?",
                "Qual o prazo?",
                "e onde ele fica?",
                "Por que?",
                // The class the other two rules structurally cannot catch: long enough not to be
                // elliptical, no connective to give it away, and the reference ("o pagamento") is a
                // definite noun, not an indefinite pronoun. It belongs in the set so this loss is
                // measured instead of assumed.
                "Quanto tempo depois do pagamento?");
        List<String> wholeQuestions = List.of(
                "Qual o prazo de entrega do contrato de suprimento do ano de 2024?",
                "Explique a diferença entre embeddings e reranking em português.",
                "Como funciona o chunking hierárquico com tokenização por sentenças?",
                "Quantas embeddings são geradas por documento de 10 páginas?",
                "O contrato de 2024 foi renovado?",
                "O que é o Score da citação?",
                "Liste as revisões do documento de política de segurança.",
                "Qual o prazo de entrega?",
                "O modelo nomic-embed-text suporta quantos tokens por chunk?",
                "Quanto custa a assinatura mensal do plano?",
                "Quais são os campos do KnowledgeBase no banco?",
                "Quais documentos precisam ser assinados para abrir um CNPJ?");

        int[] matrix = new int[4]; // tp, fp, tn, fn
        for (String question : continuations) {
            if (FollowUpQueryHeuristic.decide(question, true).compress()) {
                matrix[0]++;
            } else {
                matrix[3]++;
            }
        }
        for (String question : wholeQuestions) {
            if (FollowUpQueryHeuristic.decide(question, true).compress()) {
                matrix[1]++;
            } else {
                matrix[2]++;
            }
        }
        int truePositive = matrix[0];
        int falsePositive = matrix[1];
        int trueNegative = matrix[2];
        int falseNegative = matrix[3];

        assertThat(falsePositive)
                .as("continuations caught=%d, whole questions wrongly rewritten=%d, whole questions left "
                        + "alone=%d, continuations missed=%d — a wasted rewrite is cheap, a missed one "
                        + "is the bug this was written for, so the trade only holds while false "
                        + "positives stay at zero on this set", truePositive, falsePositive, trueNegative,
                        falseNegative)
                .isZero();
        assertThat(falseNegative)
                .as("a continuation nobody rewrites retrieves nothing and the model answers \"nao recebi "
                        + "contexto\" — missed=%d out of %d. The gate is one, which is what the rules "
                        + "actually achieve on this set: a second miss means a rule changed and nobody "
                        + "measured the cost", falseNegative, truePositive + falseNegative)
                .isLessThanOrEqualTo(1);
    }
}
