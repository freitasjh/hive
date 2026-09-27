package br.com.systec.hive.knowledge.api.domain;

/**
 * How a retrieval must treat the similarity cut-off — as a <b>policy</b>, not as a number.
 *
 * <h3>Why the number was not enough</h3>
 *
 * <p>This used to travel as {@code double minScore}, and "you do not get to choose, resolve it from
 * the base's model" was spelled {@code MIN_SCORE_FROM_KB_CONVENTION = 0d}. That is a collision of
 * meanings in a single value: {@code 0} is simultaneously "I have no opinion" and the most natural
 * thing for a future boundary to read as "no cut-off". Nothing in the type said which was meant, so a
 * caller that passed {@code 0} expecting every chunk got the convention table's number instead, and a
 * caller that passed {@code 0} expecting the convention got it by coincidence rather than by
 * statement. The decision was invisible in both directions — which is precisely how a per-model
 * cut-off was hardcoded in the wrong place once already.
 *
 * <p>A sealed hierarchy makes the two intentions unrepresentable-as-each-other: there is no value
 * that is both a number and an absence.
 *
 * <h3>What this type deliberately does not do</h3>
 *
 * <p>It does not resolve anything. Turning a policy into a number needs the base's embedding model,
 * and the model is only known inside the knowledge module ({@code EmbeddingTaskConvention}) — so
 * resolution stays there and the policy travels as policy. That is the whole point: the caller
 * states an intention, and the one layer that can read the model decides the number.
 *
 * @param threshold the cut-off policy; {@code null} is read as {@link ResolveFromKbConvention},
 *                 because "nobody said" has always meant "the base's model decides"
 */
public record RagQuerySettings(Threshold threshold) {

    /** The shared "let the base's model decide" policy. */
    public static final RagQuerySettings FROM_KB_CONVENTION =
            new RagQuerySettings(new ResolveFromKbConvention());

    public RagQuerySettings {
        if (threshold == null) {
            threshold = new ResolveFromKbConvention();
        }
    }

    public static RagQuerySettings fromKbConvention() {
        return FROM_KB_CONVENTION;
    }

    /**
     * An explicit cut-off, used verbatim.
     *
     * <p>What this now permits that the old {@code double} parameter did not is nothing at all:
     * before the sentinel was removed, {@code 0} and every negative were read as "unset" and silently
     * replaced by the base's row, so an explicit zero was unreachable <em>and</em> a caller who asked
     * for "no cut-off" was answered with the calibrated threshold instead. A caller that wants a
     * no-cut-off search has no way to say so here — and that is the honest answer, because the
     * threshold is also reported on the result and on the diagnostic, and a zero there would make
     * the number in the log a fiction. See {@link Exact} for why the range is enforced at
     * construction.
     */
    public static RagQuerySettings exact(double value) {
        return new RagQuerySettings(new Exact(value));
    }

    public boolean isFromKbConvention() {
        return threshold instanceof ResolveFromKbConvention;
    }

    /**
     * The number this policy carries, or a failure when it carries none.
     *
     * <p>For the knowledge module's own resolution: it pattern-matches the sealed interface instead,
     * so the "no number here" case is handled once, where the model is available.
     */
    public double exactValue() {
        if (threshold instanceof Exact exact) {
            return exact.value();
        }
        throw new IllegalStateException("cut-off is not explicit: resolve it from the base's embedding model");
    }

    /**
     * The two intentions, closed.
     *
     * <p>Sealed so that a {@code switch} over it is exhaustive: adding a third way to express a
     * cut-off becomes a compile error at every site that has to reason about one, instead of a
     * silent fallthrough.
     */
    public sealed interface Threshold permits ResolveFromKbConvention, Exact {
    }

    /** "The caller did not choose a cut-off" — resolve one from the base's model. */
    public record ResolveFromKbConvention() implements Threshold {
    }

    /**
     * "The caller chose this cut-off" — use it verbatim.
     *
     * <p>The range is checked <b>here, at construction</b>, and not where the number is resolved. The
     * name says the value is exactly what the caller asked for, so a value that is not a similarity
     * has to fail before it becomes an {@code Exact} — silently substituting the model's row for it
     * would be the very collision this type was introduced to remove, reappearing one layer down. A
     * {@code 0} parsed out of a form, or a negative left over from a subtraction, is a wiring mistake
     * and deserves a stack trace at the line that made it, not a search that quietly used somebody
     * else's number.
     */
    public record Exact(double value) implements Threshold {

        public Exact {
            if (!(value > 0d && value <= 1d)) {
                throw new IllegalArgumentException(
                        "cut-off must be a similarity in (0,1]: " + value);
            }
        }
    }
}
