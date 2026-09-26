package br.com.systec.hive.agent.impl.rag;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Decides whether a question looks like a continuation of the conversation, and is the reason the
 * query compressor runs at all.
 *
 * <h3>This is our judgement, not the tutorial's</h3>
 *
 * <p>The official {@code CompressingQueryTransformer} is unconditional: the documentation says to use
 * it, and every call it makes is one extra LLM round-trip per question. Unconditional is the wrong
 * default here for a measured reason — retrieval in this project costs ~57ms and a chat turn costs
 * 6–13s in LLM generation, and the project's own requirement is that <em>the search may not become
 * slow</em>. A rewrite that fires on every question would put seconds in front of the search to fix a
 * problem that only exists on follow-up questions. So the transformer is gated here, and this class is
 * the gate: a pure function of the question, with no model call, that a unit test can hold to account.
 *
 * <h3>The signals, and what each one costs when it is wrong</h3>
 *
 * <ul>
 *   <li><b>leading connective</b> ({@code e}, {@code então}, {@code por que}, {@code qual o melhor},
 *       …) — the most reliable signal, because a question that opens with a conjunction is grammatically
 *       incomplete on its own. Wrong when: the conjunction is part of a full question
 *       ({@code "e qual o melhor embeddings para pt-BR?"} mid-conversation) → one wasted rewrite.</li>
 *   <li><b>indefinite reference</b> ({@code ele}, {@code isso}, {@code o mesmo}, {@code a outra}, …) —
 *       an unresolved reference, which is the actual failure the compressor exists to fix. Wrong when:
 *       the letters show up inside a longer word ({@code "Qual a forma de elite do contrato?"}) → one
 *       wasted rewrite, which the word anchoring below is there to prevent.</li>
 *   <li><b>elliptical question</b> — a question too short to stand on its own, hence {@link
 *       #ELLIPTICAL_LIMIT} and the measurement behind it.</li>
 * </ul>
 *
 * <p><b>Where the length limit comes from.</b> A first cut used 60 characters — "shorter questions
 * are more likely continuations" — and a labelled set killed it: <b>9 of 12 self-contained questions
 * fired</b> (precision 0.57), because ordinary questions are short too
 * ({@code "Quanto custa a assinatura mensal do plano?"}, 42 characters, is a complete question). The
 * limit is now {@link #ELLIPTICAL_LIMIT} = 20: on the labelled set it catches <b>12 of 12
 * self-contained-question rejections (0 false positives)</b> and <b>12 of 13 continuations</b>, with a
 * margin rather than a knife edge — the longest question caught by length alone is 14 characters
 * ({@code "Qual o prazo?"}), the shortest self-contained one is 24
 * ({@code "Qual o prazo de entrega?"}, where the rule starts leaking again), and the one continuation
 * these rules miss is long and has no connective and no pronoun to give away
 * ({@code "Quanto tempo depois do pagamento?"}, 38 characters).
 *
 * <p>Those numbers are from {@code FollowUpQueryHeuristicTest} on a set of 25 hand-labelled questions,
 * <b>not</b> from production traffic. The log line the transformer writes is what would measure the
 * real distribution; this is the number to compare it against. The limit is a property
 * ({@code hive.rag.elliptical-limit}) so that measurement can move it without a code change.
 *
 * <p>A wrong positive costs one cheap LLM call. A wrong negative costs a follow-up that retrieves
 * nothing and the model answers "não recebi contexto" — the bug this was written for. The asymmetry
 * is deliberate and is the reason the rule set is an OR: the cheap error is preferred to the
 * expensive one, and the reason that fired is logged with every decision so the operator can see which
 * rule is over-firing in production.
 *
 * <p>All matching is word-anchored, so a reference inside a longer word does not count.
 */
public final class FollowUpQueryHeuristic {

    /**
     * A question at or below this many characters is elliptical: it cannot name a subject, a predicate
     * and an object at once, so it is either one word, a bare wh-phrase or a conjunction plus a noun —
     * all of which read as a continuation.
     *
     * <p>Chosen by measurement, not intuition. At 60 characters the rule fired on 9 of 12
     * self-contained questions in the labelled set ({@code FollowUpQueryHeuristicTest}); at 20 it
     * separates them, with the longest continuation it catches at 14 characters and the shortest
     * self-contained question it must not catch at 24. It is a single named constant so a
     * production measurement can move it without hunting for literals.
     */
    public static final int ELLIPTICAL_LIMIT = 20;

    /**
     * Question openers that leave the question grammatically dependent on what came before.
     *
     * <p>Longest first, so the reason that reaches the log is the most specific one that matched
     * ({@code "e quando"} rather than a bare {@code "e"}). Accented and unaccented spellings are both
     * listed because users type both, and a heuristic that only knows the accented form fails silently
     * on a whole class of real input. Words that open a <em>complete</em> question on their own
     * ({@code porque} without the space, {@code como}) are deliberately absent: they would fire on
     * self-contained questions, which is the error this gate is trying not to make.
     */
    private static final List<String> LEADING_CONNECTIVES = List.of(
            "qual o melhor", "qual a melhor",
            "além disso", "alem disso",
            "e quando",
            "por que",
            "e qual", "e como", "e onde",
            "então", "entao",
            "além", "alem",
            "mas", "e");

    /**
     * Words that refer to something already mentioned, matched anywhere in the question.
     *
     * <p>Pre-compiled: the patterns are built once, because this runs on the hot path of every
     * question that reaches the retrieval, and sixteen regex compilations per question is a cost
     * nobody can see and everybody pays.
     */
    private static final List<Signal> INDEFINITE_REFERENCES = references(
            "ele", "ela", "eles", "elas", "isso", "isto", "aquilo",
            "o mesmo", "a mesma", "o outro", "a outra", "dele", "dela", "deles", "delas");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Question marks, dots and exclamation marks at the end, and any run of them. */
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[?!.]+$");

    private FollowUpQueryHeuristic() {
    }

    /**
     * @param hasHistory whether the turn has previous messages; a first question has nothing to be a
     *                   continuation of, so no signal can make it one
     */
    public static Decision decide(String question, boolean hasHistory) {
        return decide(question, hasHistory, ELLIPTICAL_LIMIT);
    }

    /**
     * @param ellipticalLimit the length at or below which a question is elliptical; exposed because it
     *                         is configurable and a test that could only exercise the shipped number
     *                         would not notice the parameter being ignored
     */
    public static Decision decide(String question, boolean hasHistory, int ellipticalLimit) {
        if (question == null || question.isBlank()) {
            return new Decision(false, "empty_question");
        }
        if (!hasHistory) {
            return new Decision(false, "no_history");
        }
        String normalized = normalize(question);
        for (String connective : LEADING_CONNECTIVES) {
            if (startsWith(normalized, connective)) {
                return new Decision(true, "leading_connective:" + connective.strip());
            }
        }
        for (Signal reference : INDEFINITE_REFERENCES) {
            if (reference.pattern().matcher(normalized).find()) {
                return new Decision(true, "indefinite_reference:" + reference.word());
            }
        }
        if (normalized.length() <= ellipticalLimit) {
            return new Decision(true, "elliptical<=" + ellipticalLimit);
        }
        return new Decision(false, "no_signal");
    }

    /**
     * Lower case, single spaces, and no trailing question mark.
     *
     * <p>The punctuation matters more than it looks: every rule here matches a word at a boundary, and
     * a bare {@code "Então?"} ends in a mark, so without stripping it the question is "então?" — a
     * word that matches neither the connective {@code então} nor anything else, and a continuation that
     * the gate would miss. Whitespace is collapsed for the same reason: a question typed with a double
     * space or a line break is the same question.
     */
    private static String normalize(String question) {
        return TRAILING_PUNCTUATION.matcher(WHITESPACE.matcher(question.strip())
                .replaceAll(" ")).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(String normalized, String prefix) {
        return normalized.startsWith(prefix + " ") || normalized.equals(prefix);
    }

    private static List<Signal> references(String... words) {
        return Stream.of(words)
                .map(word -> new Signal(word, Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word)
                        + "(?![\\p{L}\\p{N}])")))
                .toList();
    }

    /**
     * A word and the pattern that finds it as a whole word. The anchors are what keep
     * {@code "modelo"} from reading as a reference to {@code "ele"}.
     */
    private record Signal(String word, Pattern pattern) {
    }

    /**
     * @param compress whether the query should be rewritten
     * @param reason   which rule decided it, written to be read in a log line: either
     *                 {@code rule:signal} for a positive, or a plain cause for a negative
     */
    public record Decision(boolean compress, String reason) {
    }
}
