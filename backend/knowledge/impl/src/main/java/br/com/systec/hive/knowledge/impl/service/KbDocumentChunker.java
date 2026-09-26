package br.com.systec.hive.knowledge.impl.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.TokenCountEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Turns a parsed document into the chunks that get embedded, using LangChain4j's own splitter.
 *
 * <h3>What replaced what</h3>
 *
 * <p>This class replaces a hand-rolled whitespace splitter that filled a
 * {@code CHUNK_TOKENS * 4}-character budget. That budget was the bug in one line: 512 is a
 * <i>token</i> count, it was being enforced in <i>characters</i>, and the two only agree for English
 * prose &mdash; see {@link KbTokenCountEstimator} for what the ratio costs in other languages.
 *
 * <h3>Why the official splitter, and what it actually is</h3>
 *
 * <p>{@code DocumentSplitters.recursive} is not one splitter. In the 1.19.0 jar it composes four
 * (verified by disassembling it): {@code DocumentByParagraphSplitter} over
 * {@code DocumentByLineSplitter} over {@code DocumentBySentenceSplitter} over
 * {@code DocumentByWordSplitter}, each falling through to the next only when the unit above it is
 * too big for one segment. So a boundary is placed at the coarsest unit that fits &mdash; a
 * paragraph if it fits, a sentence if it does not, a word only as a last resort. The old code had
 * exactly one unit, whitespace, which is why chunks began and ended mid-sentence: it had no way to
 * know a sentence had ended.
 *
 * <p>Overlap follows from the same hierarchy: it is assembled from whole sentences
 * ({@code HierarchicalDocumentSplitter.overlapFrom}), not from the last N words.
 *
 * <p>One inherited behaviour to know about: a <i>single word</i> longer than the budget is handed
 * to a character splitter, and that does cut mid-word. It only happens for text with no whitespace
 * at all for ~2000 characters (an embedded data blob, a very long URL), where cutting the word is
 * the lesser evil &mdash; the alternative the old code chose was emitting the oversized token whole
 * and letting the embedding model truncate it.
 *
 * <h3>Why the title is prepended</h3>
 *
 * <p>Segments are transformed <b>after</b> splitting through a {@link TextSegmentTransformer}, so
 * every segment receives the header no matter which level of the hierarchy produced it &mdash; the
 * LangChain4j RAG tutorial's pattern. See {@link KbDocumentTitle} for where the title comes from
 * and why a fragment without a subject is a hallucination risk.
 */
public final class KbDocumentChunker {

    /** Kept at the value the ingestion already used, so this change moves boundaries, not budgets. */
    public static final int MAX_SEGMENT_TOKENS = 512;

    /** Kept for the same reason. Applied in whole sentences by the official hierarchy. */
    public static final int CHUNK_OVERLAP_TOKENS = 50;

    /** Floor for the body budget, so an absurdly long title cannot ask for a non-positive size. */
    private static final int MIN_BODY_TOKENS = 64;

    private static final String HEADER_SEPARATOR = "\n\n";

    private static final Logger log = LoggerFactory.getLogger(KbDocumentChunker.class);

    private final TokenCountEstimator tokenCountEstimator = KbTokenCountEstimator.getInstance();

    public List<String> chunk(String title, String fileName, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String header = KbDocumentTitle.header(title, fileName);
        int budget = bodyBudget(header);
        warnIfFallbackWillFire(text, budget, title);
        String body = withoutTitlePrefix(text, title);
        DocumentSplitter splitter =
                DocumentSplitters.recursive(budget, CHUNK_OVERLAP_TOKENS, tokenCountEstimator);
        if (header.isEmpty()) {
            return withoutHeader(splitter, body);
        }
        return withHeader(splitter, body, header);
    }

    /**
     * Makes the character-splitting fallback visible, because what it produces is not what this
     * class otherwise promises.
     *
     * <h3>What actually happens, characterised</h3>
     *
     * <p>A whitespace-free run longer than the budget cannot be split into words, so
     * {@code DocumentByWordSplitter} hands it to {@code DocumentByCharacterSplitter} and the run is
     * cut <b>mid-word</b>. Around that cut the upstream hierarchy also emits two artefacts, both
     * observed on a 2500-character base64 blob:
     *
     * <ul>
     *   <li><b>byte-identical consecutive chunks</b> &mdash; the overlap region is flushed as a
     *       chunk of its own and then re-emitted, so the same text is stored twice under two
     *       different {@code chunk_index} values, producing two identical vectors;</li>
     *   <li><b>four-token chunks</b> &mdash; the sentence that preceded the blob ("Introducao.")
     *       and the one after it ("Conclusao.") each become a chunk whose entire content is that
     *       fragment. Four tokens is the exact defect this change set out to remove, reappearing
     *       through a different door.</li>
     * </ul>
     *
     * <p>None of this is fixed here. It is upstream, in the jar, and patching a splitter hierarchy
     * is not a change this step is entitled to make. What it must not be is <b>silent</b>: a
     * document that chunks this way is a document whose index is partly garbage, and an operator
     * has to be able to find it from a log rather than from a user's complaint about a bad answer.
     *
     * <h3>Why the scan is cheap</h3>
     *
     * <p>The check is a character-length filter, not a token count of the whole document. Every
     * token covers at least one character, so {@code count(word) <= word.length()} always holds and
     * a run can only exceed the budget if it is longer than the budget in <i>characters</i>. The
     * filter therefore discards ordinary words without tokenizing them, and only the few surviving
     * candidates are measured.
     *
     * @return the diagnostic, for tests; production callers read the log
     */
    FallbackDiagnostic warnIfFallbackWillFire(String text, int budget, String title) {
        int offendingRuns = 0;
        int longestRunChars = 0;
        for (String candidate : text.split("\\s+")) {
            if (candidate.length() <= budget) {
                continue;
            }
            offendingRuns++;
            longestRunChars = Math.max(longestRunChars, candidate.length());
        }
        if (offendingRuns == 0) {
            return FallbackDiagnostic.NONE;
        }
        FallbackDiagnostic diagnostic = new FallbackDiagnostic(offendingRuns, longestRunChars);
        log.warn("kb chunk fallback: {} whitespace-free run(s) exceed the {} token budget "
                        + "(longest {} chars) and will be cut mid-word; surrounding text may produce "
                        + "duplicate and very small chunks. document={}",
                diagnostic.oversizedRuns(), budget, diagnostic.longestRunChars(),
                title == null || title.isBlank() ? "(untitled)" : title);
        return diagnostic;
    }

    /**
     * What the scan found, so a test can assert on the diagnostic without reading a log.
     *
     * @param oversizedRuns  how many whitespace-free runs exceed the budget
     * @param longestRunChars the longest of them, in characters
     */
    public record FallbackDiagnostic(int oversizedRuns, int longestRunChars) {

        static final FallbackDiagnostic NONE = new FallbackDiagnostic(0, 0);

        public boolean fallbackExpected() {
            return oversizedRuns > 0;
        }
    }

    /** Token count of a stored chunk, for the ingestion log. */
    public int estimateTokens(String text) {
        return tokenCountEstimator.estimateTokenCountInText(text);
    }

    /**
     * Drops the opening title line from the body, because the header already carries it.
     *
     * <p>Without this the title becomes a chunk of its own — twice, in fact, because the overlap
     * logic re-emits it. Both copies are then embedded: vectors carrying nothing but the title,
     * which match any question that happens to name the document and dilute the base with content
     * that was never in it. The header is the title's job; the body's job is the prose.
     *
     * <p>Only the line that <b>is</b> the title is removed, and only when it is the first
     * non-blank one. A document that is nothing but a title keeps its text — dropping it would
     * store zero chunks and still report the document as indexed.
     */
    private String withoutTitlePrefix(String text, String title) {
        if (title == null || title.isBlank()) {
            return text;
        }
        String normalized = title.strip();
        StringBuilder rest = new StringBuilder();
        boolean dropped = false;
        for (String line : text.lines().toList()) {
            if (!dropped) {
                if (line.isBlank()) {
                    continue;
                }
                if (!line.strip().equals(normalized)) {
                    return text;
                }
                dropped = true;
                continue;
            }
            rest.append(line).append('\n');
        }
        String candidate = rest.toString().strip();
        return candidate.isEmpty() ? text : candidate;
    }

    /**
     * The body budget is what is left of the segment after the header, so the <b>stored</b> chunk
     * respects {@link #MAX_SEGMENT_TOKENS} &mdash; the header is embedded too, and the number the
     * embedding model gets to see is the one that has to fit.
     *
     * <p>The prefix is measured as it will actually be concatenated, separator included, rather
     * than as the header alone. Tokenization is not additive across a join: budgeting
     * {@code header + "\n\n" + body} as {@code count(header) + count(body)} let chunks through at
     * 513 tokens against a 512 budget, because the separator carries its own token. Measuring the
     * prefix as one string makes the reservation exact for whatever the tokenizer does at that
     * boundary.
     */
    private int bodyBudget(String header) {
        int prefixTokens = tokenCountEstimator.estimateTokenCountInText(header + HEADER_SEPARATOR);
        return Math.max(MIN_BODY_TOKENS, MAX_SEGMENT_TOKENS - prefixTokens);
    }

    private List<String> withHeader(DocumentSplitter splitter, String text, String header) {
        TextSegmentTransformer prefixingTitle =
                segment -> TextSegment.from(header + HEADER_SEPARATOR + segment.text(), segment.metadata());
        return splitter.splitAll(Document.from(text)).stream()
                .map(prefixingTitle::transform)
                .map(TextSegment::text)
                .filter(chunk -> !chunk.isBlank())
                .toList();
    }

    private List<String> withoutHeader(DocumentSplitter splitter, String text) {
        return splitter.splitAll(Document.from(text)).stream()
                .map(TextSegment::text)
                .filter(chunk -> !chunk.isBlank())
                .toList();
    }
}
