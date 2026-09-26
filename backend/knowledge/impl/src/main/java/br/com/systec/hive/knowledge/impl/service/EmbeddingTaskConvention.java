package br.com.systec.hive.knowledge.impl.service;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The one place that answers "how do I have to talk to this model, and how close is close enough
 * to count as a hit": the task prefix on each side, and the similarity cut-off its scores justify.
 *
 * <h3>Why a rule table and not an {@code if}</h3>
 *
 * The policy is data, not control flow: one row per model family, each row carrying its own
 * prefixes, its own cut-off and the reason they exist. A chain of {@code if (model.contains("nomic"))}
 * looks cheaper and is not: adding a family means finding the right branch in a method that also
 * resolves credentials, picks a vendor and logs — and the day a second family needs a prefix, the
 * branches start sharing a "not OpenAI" condition that is wrong by accident. Here a family is a row,
 * the lookup is a scan over a handful of immutable entries, and both defaults (no prefix, strict
 * cut-off) are the safe ones because an unknown model must not be handed text it was never trained
 * to receive nor judged by a number measured on a different model.
 *
 * <h3>The table</h3>
 *
 * <table border="1">
 *   <caption>Task prefix and default similarity cut-off, per model family</caption>
 *   <tr>
 *     <th>Model family</th><th>Document</th><th>Query</th><th>Default cut-off</th><th>Why</th>
 *   </tr>
 *   <tr>
 *     <td>{@code nomic*}</td>
 *     <td>{@code search_document: }</td>
 *     <td>{@code search_query: }</td>
 *     <td>{@value #NOMIC_SIMILARITY_THRESHOLD}</td>
 *     <td>trained with Matryoshka task prefixes; each side is projected into its own sub-space and
 *         the two only become comparable when each carries its own prefix
 *         (nomic.ai/docs/concepts/embeddings). Its scores also sit lower across the board — see
 *         {@link #NOMIC_SIMILARITY_THRESHOLD}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code text-embedding-3-*} (OpenAI)</td>
 *     <td>none</td>
 *     <td>none</td>
 *     <td>{@value #DEFAULT_SIMILARITY_THRESHOLD}</td>
 *     <td>no task prefix exists for it — prepending one is out-of-distribution text and measurably
 *         degrades the vectors. Its cut-off is the historical one, which is also the conservative
 *         default, so it needs no row of its own</td>
 *   </tr>
 *   <tr>
 *     <td>everything else (mxbai, minilm, custom)</td>
 *     <td>none</td>
 *     <td>none</td>
 *     <td>{@value #DEFAULT_SIMILARITY_THRESHOLD}</td>
 *     <td>no prefix requirement documented for these families; an unknown model gets raw text, and
 *         a cut-off we have not measured for it has to be the strict one</td>
 *   </tr>
 * </table>
 *
 * <h3>Why the cut-off is a column of this table and not a constant elsewhere</h3>
 *
 * <p>The number is a property of the <b>model family</b>, exactly like the prefixes, and it used to
 * live in two constants ({@code ChatServiceImpl.RAG_MIN_SCORE} and
 * {@code KbRetrievalService.DEFAULT_MIN_SCORE}) that both held {@code 0.7} and neither explained
 * where it came from. One cut-off for every model is wrong by construction: cosine similarity is
 * only comparable inside the space its model produces, and the families do not produce the same
 * space. On a real {@code nomic-embed-text} base, measured after the task prefix reached both sides
 * ({@code 1 - (embedding <=> query)}):
 *
 * <pre>
 * relevant long question        0.6481
 * relevant short question       0.6219   <- a 0.70 cut-off rejects this one
 * unrelated ("banco de dados")  0.5972   <- a 0.60 cut-off keeps this one out
 * unrelated ("protein folding") 0.5851
 * </pre>
 *
 * <p>A single 0.70 makes the service throw away correct answers for this family; a single 0.60
 * starts admitting noise. Note the margin is about {@code 0.02} and the sample was five chunks of
 * one small PDF — this is a cut-off that works on the evidence available, not a comfortable one,
 * and it is expected to be re-measured when a larger base is available. What must not happen is a
 * value chosen to look rounder than the measurement.
 *
 * <p>OpenAI keeps {@value #DEFAULT_SIMILARITY_THRESHOLD} — that is the behaviour it always had, so
 * naming it as a row would be a change disguised as documentation.
 *
 * <p>This table answers the prefix and the default-cut-off question. It is deliberately <b>not</b> a
 * list of supported models: routing and dimension support are narrower. {@code bge} is the clearest
 * case — the family is served in 384, 768 and 1024 dimensions while this project only stores 768 and
 * 1536, so {@link KbEmbeddingModelFactory#isOllamaModel} does not route it to Ollama: a base
 * declared with a {@code bge} name would get a dimension that can never match the vector the model
 * returns, and fail at ingest with a mismatch instead of at configuration with a clear message.
 *
 * <p><b>P2 risk:</b> this convention is not versioned per knowledge base. Changing a row moves the
 * space of every vector already written under the old row, and a base holding vectors from two
 * conventions produces undefined scores. Every stored row carries the convention that wrote it (see
 * {@link KbEmbeddingStore#metadataJson}), so a mixed base is detectable:
 *
 * <pre>{@code
 * SELECT kb_id, metadata->>'convention' AS convention, count(*)
 * FROM kb_embeddings_768
 * WHERE tenant_id = ?
 * GROUP BY 1, 2
 * ORDER BY 1;
 * -- more than one row per kb_id: mixed space, reindex the whole base
 * }</pre>
 */
public final class EmbeddingTaskConvention {

    /** Nomic's document-side prefix (trailing space included: it is part of the prompt). */
    public static final String NOMIC_DOCUMENT_PREFIX = "search_document: ";

    /** Nomic's query-side prefix. */
    public static final String NOMIC_QUERY_PREFIX = "search_query: ";

    /** Reported for every model that is not listed in {@link #RULES}. */
    public static final String PLAIN = "plain";

    /**
     * Cut-off measured for the {@code nomic*} family on a real base: below {@code 0.60} the matches
     * stop being relevant (0.6219 for a real short question, 0.5972 for a question with nothing to
     * do with the base). Lower than every other family, because this family's scores are.
     */
    public static final double NOMIC_SIMILARITY_THRESHOLD = 0.60;

    /**
     * Cut-off for every family this table does not name, including {@code text-embedding-3-*}. It
     * is the value the project has always used and the strict one, which is the only defensible
     * choice for a model whose score distribution nobody has measured.
     */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.70;

    private record Rule(String id, Predicate<String> matches, String documentPrefix, String queryPrefix,
                        double defaultThreshold) {
    }

    private static final Rule PLAIN_RULE =
            new Rule(PLAIN, model -> false, "", "", DEFAULT_SIMILARITY_THRESHOLD);

    /** Every prefix this table knows, in both roles — used to recognise a foreign one. */
    private static final List<String> KNOWN_PREFIXES =
            List.of(NOMIC_DOCUMENT_PREFIX, NOMIC_QUERY_PREFIX);

    /**
     * One row per model family that needs a task prefix or its own cut-off. The first match wins, so
     * a more specific family has to be listed before a broader one.
     */
    private static final List<Rule> RULES = List.of(
            new Rule("nomic-task-prefix", model -> model.contains("nomic"),
                    NOMIC_DOCUMENT_PREFIX, NOMIC_QUERY_PREFIX, NOMIC_SIMILARITY_THRESHOLD)
    );

    private EmbeddingTaskConvention() {
    }

    /** Prefix a stored text must carry for {@code model}; empty when the model takes raw text. */
    public static String documentPrefix(String model) {
        return ruleFor(model).documentPrefix();
    }

    /** Prefix a search text must carry for {@code model}; empty when the model takes raw text. */
    public static String queryPrefix(String model) {
        return ruleFor(model).queryPrefix();
    }

    /**
     * Similarity cut-off to apply to {@code model} when the caller did not ask for one.
     *
     * <p>Always the conservative {@link #DEFAULT_SIMILARITY_THRESHOLD} for a family this table does
     * not name: an unmeasured model must not get the benefit of a number tuned on another family's
     * score distribution.
     */
    public static double defaultThreshold(String model) {
        return ruleFor(model).defaultThreshold();
    }

    /** Identifier of the rule that matched, for logs. {@link #PLAIN} when nothing matched. */
    public static String conventionId(String model) {
        return ruleFor(model).id();
    }

    /**
     * Single log-ready description of the convention in force for {@code model}, so an operator
     * reading one log line can tell which space the vectors of that model live in.
     */
    public static String describe(String model) {
        Rule rule = ruleFor(model);
        if (PLAIN.equals(rule.id())) {
            return PLAIN;
        }
        return rule.id() + " document='" + rule.documentPrefix() + "' query='" + rule.queryPrefix() + "'";
    }

    /**
     * Prefixes {@code text} for the provider, idempotently: a text that already carries
     * {@code prefix} is returned untouched.
     *
     * <p>Idempotence is not a nicety here. The query is <b>user input</b>, so someone who types
     * {@code search_query: como reinicio a busca?} would otherwise hand the model
     * {@code search_query: search_query: ...}.
     *
     * <p>A text carrying the <em>other</em> role's prefix is prefixed anyway — see
     * {@link #hasForeignRolePrefix}. Rejecting it would turn harmless user text into a failed
     * retrieval, and stripping it would delete content the document really contains.
     *
     * <p>A null text is returned untouched so the caller fails where it would have failed anyway,
     * instead of sending a literal "null" to a paid endpoint.
     */
    public static String apply(String prefix, String text) {
        if (text == null || prefix == null || prefix.isEmpty()) {
            return text;
        }
        if (text.startsWith(prefix)) {
            return text;
        }
        return prefix + text;
    }

    /**
     * Whether {@code text} already starts with a task prefix that is not the one its role requires —
     * a question typed as {@code search_document: ...}, or a document pasted from a nomic example.
     *
     * <p>Used only to make the anomaly visible. The text is still sent (see {@link #apply}): it
     * belongs to the user, and quietly dropping or rewriting it would be a worse outcome than a
     * doubled prefix on one rare input.
     */
    public static boolean hasForeignRolePrefix(String prefix, String text) {
        if (text == null || prefix == null || prefix.isEmpty()) {
            return false;
        }
        for (String known : KNOWN_PREFIXES) {
            if (text.startsWith(known) && !known.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Rule ruleFor(String model) {
        String normalized = model == null ? "" : model.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.matches().test(normalized)) {
                return rule;
            }
        }
        return PLAIN_RULE;
    }
}
