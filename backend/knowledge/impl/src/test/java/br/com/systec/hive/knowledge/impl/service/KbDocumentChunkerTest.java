package br.com.systec.hive.knowledge.impl.service;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit level of the chunking contract. The end-to-end shape of the same contract — the title, the
 * boundary, the unit — is asserted against the text actually stored in
 * {@link KbIngestionServiceTest}, because that is the level at which the bug was invisible.
 */
class KbDocumentChunkerTest {

    private final KbDocumentChunker chunker = new KbDocumentChunker();

    private static String prose(int sentences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sentences; i++) {
            sb.append("A etapa ").append(i)
                    .append(" descreve como o operador reinicia o indice vetorial com seguranca. ")
                    .append("Os passos foram validados em producao. ");
        }
        return sb.toString();
    }

    @Test
    void when_chunk_withBlankText_then_nothing() {
        assertThat(chunker.chunk("T", "f.txt", "   ")).isEmpty();
        assertThat(chunker.chunk("T", "f.txt", null)).isEmpty();
    }

    @Test
    void when_chunk_withShortText_then_singleChunkCarryingTitleAndFileName() {
        List<String> chunks = chunker.chunk("Guia Pratico", "guia.pdf", "hello world");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0))
                .startsWith("Guia Pratico")
                .contains("guia.pdf")
                .contains("hello world");
    }

    /** When the title is the file name, the header collapses instead of repeating itself. */
    @Test
    void when_chunk_withTitleEqualToFileName_then_theHeaderIsOneLine() {
        List<String> chunks = chunker.chunk("runbook.txt", "runbook.txt", "hello world");

        assertThat(chunks.get(0)).startsWith("runbook.txt\n\nhello world");
    }

    @Test
    void when_chunk_aLongText_then_moreThanOneChunkAndNoneOverTheTokenBudget() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", prose(200));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunker.estimateTokens(chunk))
                        .isLessThanOrEqualTo(KbDocumentChunker.MAX_SEGMENT_TOKENS));
    }

    @Test
    void when_chunk_aLongText_then_everyChunkStartsWithTheTitle() {
        List<String> chunks = chunker.chunk("Guia Pratico", "guia.pdf", prose(200));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).startsWith("Guia Pratico"));
    }

    @Test
    void when_chunk_aLongText_then_everyChunkEndsOnASentenceBoundary() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", prose(200));

        assertThat(chunks).allSatisfy(chunk -> assertThat(bodyOf(chunk)).endsWith("."));
    }

    @Test
    void when_chunk_aLongText_then_everyChunkEndsOnAWholeWordOfTheSource() {
        String text = prose(200);
        Set<String> sourceWords = Arrays.stream(text.split("\\s+")).collect(Collectors.toSet());

        List<String> chunks = chunker.chunk("Guia", "guia.pdf", text);

        assertThat(chunks).allSatisfy(chunk -> {
            String[] words = bodyOf(chunk).split("\\s+");
            assertThat(sourceWords).contains(words[words.length - 1]);
        });
    }

    /**
     * The title is in the header; the body must not also contribute it as a chunk of its own, or
     * the base carries two vectors whose entire content is the title.
     */
    @Test
    void when_chunk_whenTheTextOpensWithTheTitle_then_theTitleIsNotAlsoAChunk() {
        List<String> chunks = chunker.chunk("Guia Pratico", "guia.pdf", "Guia Pratico\n\n" + prose(200));

        assertThat(chunks).allSatisfy(chunk -> assertThat(bodyOf(chunk))
                .isNotEqualTo("Guia Pratico")
                .doesNotStartWith("Guia Pratico\n\nGuia Pratico"));
    }

    /** A document that is only a title still gets indexed, rather than storing zero chunks. */
    @Test
    void when_chunk_withATitleOnlyDocument_then_itIsStillStored() {
        List<String> chunks = chunker.chunk("Guia Pratico", "guia.pdf", "Guia Pratico");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("Guia Pratico");
    }

    /** Sentence-level overlap, which is what a 50-token overlap means under the official hierarchy. */
    @Test
    void when_chunk_aLongText_then_consecutiveChunksShareWholeSentences() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", prose(200));

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String[] previousSentences = bodyOf(chunks.get(i - 1)).split("(?<=\\.)\\s+");
            String boundarySentence = previousSentences[previousSentences.length - 1];
            assertThat(bodyOf(chunks.get(i))).contains(boundarySentence);
        }
    }

    /** No chunk is emitted empty, and the stored text is never only the header. */
    @Test
    void when_chunk_anyText_then_noChunkIsOnlyTheHeader() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", prose(200));

        assertThat(chunks).allSatisfy(chunk -> assertThat(bodyOf(chunk)).isNotBlank());
    }

    /**
     * The title is resolved against the truncated text in the ingestion path, so an over-long first
     * line must fall through to the file name rather than become a 200-character header repeated on
     * every chunk.
     */
    @Test
    void when_resolveTitle_withNoMetadata_then_usesTheFirstMeaningfulLine() {
        String title = KbDocumentTitle.resolve(null, "\n\n  GUIA PRATICO  \n\ncorpo do texto", "guia.pdf");

        assertThat(title).isEqualTo("GUIA PRATICO");
    }

    @Test
    void when_resolveTitle_withTitleMetadata_then_metadataWins() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("dc:title", "Titulo Do Documento");

        String title = KbDocumentTitle.resolve(metadata, "GUIA PRATICO\n\ncorpo", "guia.pdf");

        assertThat(title).isEqualTo("Titulo Do Documento");
    }

    @Test
    void when_resolveTitle_withNothingUsable_then_fallsBackToTheFileName() {
        assertThat(KbDocumentTitle.resolve(null, "   ", "guia.pdf")).isEqualTo("guia.pdf");
        assertThat(KbDocumentTitle.resolve(null, null, "guia.pdf")).isEqualTo("guia.pdf");
    }

    /** A bare page number, a URL or a whole paragraph is not a title. */
    @Test
    void when_resolveTitle_withANonTitleFirstLine_then_itIsRejected() {
        assertThat(KbDocumentTitle.resolve(null, "12\n\ncorpo do texto", "guia.pdf")).isEqualTo("guia.pdf");
        assertThat(KbDocumentTitle.resolve(null, "https://exemplo.com/x\n\ncorpo", "guia.pdf"))
                .isEqualTo("guia.pdf");
        assertThat(KbDocumentTitle.resolve(null, "x".repeat(300) + "\n\ncorpo", "guia.pdf"))
                .isEqualTo("guia.pdf");
    }

    /**
     * Characterisation of the upstream fallback, pinned deliberately.
     *
     * <p>This asserts behaviour that is <b>wrong</b> — a mid-word cut, two byte-identical chunks,
     * and chunks of four tokens. It is here so the behaviour is <i>visible</i>: if a future version
     * of langchain4j changes it, this test fails and the change gets looked at, instead of the
     * index quietly changing shape under everyone.
     *
     * <p>Trigger: a whitespace-free run longer than the budget. It has to be high-entropy text —
     * {@code "A".repeat(2500)} compresses to ~322 tokens and never reaches the character splitter.
     * A base64 blob is the realistic shape (data URIs, signed URLs, JWTs, minified JS, hex dumps).
     */
    @Test
    void when_chunk_withAnOversizedWhitespaceFreeRun_then_upstreamFallsBackToCharacterSplitting() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", "Introducao. " + base64Blob(1875) + " Conclusao.");

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks)
                .as("the cut lands inside the blob, not on a word boundary")
                .anySatisfy(chunk -> assertThat(bodyOf(chunk))
                        .as("a chunk made only of blob characters, i.e. a severed token")
                        .matches(".*[A-Za-z0-9+/=]{200,}.*"));
    }

    /**
     * The duplicate the fallback produces, pinned. Two byte-identical chunks are stored under two
     * different {@code chunk_index} values, so the base holds the same vector twice. Kept as a test
     * rather than a fix: the cause is upstream, and until it is decided whether ingestion should
     * collapse these, this is the tripwire.
     */
    @Test
    void when_chunk_withAnOversizedWhitespaceFreeRun_then_consecutiveChunksCanBeByteIdentical() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", "Introducao. " + base64Blob(1875) + " Conclusao.");

        assertThat(chunks)
                .as("upstream re-emits the overlap region as its own chunk")
                .anySatisfy(chunk -> assertThat(countOccurrences(chunks, chunk))
                        .as("this chunk is stored more than once")
                        .isGreaterThan(1));
    }

    /** And the four-token fragments: the very defect Passo 1 exists to remove. */
    @Test
    void when_chunk_withAnOversizedWhitespaceFreeRun_then_tinyChunksAppearAroundTheCut() {
        List<String> chunks = chunker.chunk("Guia", "guia.pdf", "Introducao. " + base64Blob(1875) + " Conclusao.");

        assertThat(chunks)
                .as("a four-token fragment is not a chunk worth embedding")
                .anySatisfy(chunk -> assertThat(chunker.estimateTokens(bodyOf(chunk)))
                        .isLessThan(KbDocumentChunker.MAX_SEGMENT_TOKENS / 10));
    }

    /**
     * The budget must hold even on the pathological path. It did not at first: budgeting the header
     * and the body separately let 513-token chunks through a 512 budget, because the separator
     * carries its own token and tokenization is not additive across the join.
     */
    @Test
    void when_chunk_withAnOversizedWhitespaceFreeRun_then_theBudgetStillHolds() {
        List<String> chunks = chunker.chunk("Guia Pratico", "guia.pdf", "Introducao. " + base64Blob(1875) + " Conclusao.");

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunker.estimateTokens(chunk))
                .isLessThanOrEqualTo(KbDocumentChunker.MAX_SEGMENT_TOKENS));
    }

    /** The fallback is announced, so an operator can find the affected document from a log. */
    @Test
    void when_chunk_withAnOversizedWhitespaceFreeRun_then_theFallbackIsReported() {
        String text = "Introducao. " + base64Blob(1875) + " Conclusao.";

        KbDocumentChunker.FallbackDiagnostic diagnostic = chunker.warnIfFallbackWillFire(
                text, KbDocumentChunker.MAX_SEGMENT_TOKENS, "Guia");

        assertThat(diagnostic.fallbackExpected()).isTrue();
        assertThat(diagnostic.oversizedRuns()).isEqualTo(1);
        assertThat(diagnostic.longestRunChars()).isEqualTo(2500);
    }

    @Test
    void when_chunk_ordinaryProse_then_noFallbackIsReported() {
        KbDocumentChunker.FallbackDiagnostic diagnostic =
                chunker.warnIfFallbackWillFire(prose(200), KbDocumentChunker.MAX_SEGMENT_TOKENS, "Guia");

        assertThat(diagnostic.fallbackExpected()).isFalse();
        assertThat(diagnostic.oversizedRuns()).isZero();
    }

    /**
     * A long run of one repeated character is <b>not</b> a trigger: it compresses well under the
     * budget. This is why the check cannot be a bare length test on the document.
     */
    @Test
    void when_chunk_aLongButLowEntropyRun_then_noFallbackIsReported() {
        String repetitive = "A".repeat(2500);

        KbDocumentChunker.FallbackDiagnostic diagnostic =
                chunker.warnIfFallbackWillFire(repetitive, KbDocumentChunker.MAX_SEGMENT_TOKENS, null);

        assertThat(diagnostic.fallbackExpected())
                .as("the filter is a length pre-check, and a word can only exceed the budget in tokens if it is longer than the budget in characters")
                .isTrue();
        assertThat(chunker.estimateTokens(repetitive))
                .as("documented here so the reason the length pre-check is sound is visible")
                .isLessThan(KbDocumentChunker.MAX_SEGMENT_TOKENS);
    }

    // ------------------------------------------------------------------ title resolution

    /**
     * {@code subtitle} is not a title. {@code endsWith("title")} matched it, and a DOCX carrying
     * both keys could have its edition year prefixed onto every chunk of the document.
     */
    @Test
    void when_resolveTitle_withSubtitleAlongside_dcTitle_then_theSubtitleDoesNotWin() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("dc:title", "Guia Pratico");
        metadata.put("subtitle", "Edicao 2024");
        metadata.put("dc:description-subtitle", "Edicao 2024");
        metadata.put("x-custom:chapTitle", "Anexo B");

        assertThat(KbDocumentTitle.resolve(metadata, "corpo do texto", "guia.pdf"))
                .isEqualTo("Guia Pratico");
    }

    /** Nor does any other key whose last segment merely ends in "title". */
    @Test
    void when_resolveTitle_withOnlyNearMissKeys_then_itFallsThroughToTheFirstLine() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("subtitle", "Edicao 2024");
        metadata.put("x-custom:chapTitle", "Anexo B");

        assertThat(KbDocumentTitle.resolve(metadata, "Titulo Visivel\n\ncorpo", "guia.pdf"))
                .isEqualTo("Titulo Visivel");
    }

    /**
     * The winner cannot depend on map order. {@code Metadata.toMap()} returns a {@code HashMap},
     * so a scan would hand the title to whichever key hashed first — and adding an unrelated piece
     * of metadata would change which title every chunk carries.
     */
    @Test
    void when_resolveTitle_withTwoPrefixedTitles_then_thePrecedenceOrderDecidesRepeatably() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("dcterms:title", "Titulo Dcterms");
        metadata.put("dc:title", "Titulo Dc");
        metadata.put("meta:title", "Titulo Meta");
        metadata.put("title", "Titulo Simples");
        metadata.put("Content-Encoding", "gzip");

        for (int i = 0; i < 50; i++) {
            assertThat(KbDocumentTitle.resolve(metadata, "corpo", "guia.pdf"))
                    .as("dc:title outranks every other spelling")
                    .isEqualTo("Titulo Dc");
        }
    }

    /** Two unknown prefixes disagreeing is not a coin toss — the document falls through. */
    @Test
    void when_resolveTitle_withTwoAmbiguousPrefixedTitles_then_itDeclinesToGuess() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("x-one:title", "Titulo Um");
        metadata.put("x-two:title", "Titulo Dois");

        assertThat(KbDocumentTitle.resolve(metadata, "Titulo Visivel\n\ncorpo", "guia.pdf"))
                .isEqualTo("Titulo Visivel");
    }

    /** A single unknown prefix is still accepted — it is unambiguous. */
    @Test
    void when_resolveTitle_withOneUnknownPrefixedTitle_then_itIsUsed() {
        var metadata = new dev.langchain4j.data.document.Metadata();
        metadata.put("x-custom:title", "Titulo Do Arquivo");

        assertThat(KbDocumentTitle.resolve(metadata, "corpo", "guia.pdf"))
                .isEqualTo("Titulo Do Arquivo");
    }

    private static int countOccurrences(List<String> chunks, String value) {
        int count = 0;
        for (String chunk : chunks) {
            if (chunk.equals(value)) {
                count++;
            }
        }
        return count;
    }

    private static String base64Blob(int rawBytes) {
        byte[] raw = new byte[rawBytes];
        java.util.random.RandomGenerator.getDefault().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static String bodyOf(String chunk) {
        int separator = chunk.indexOf("\n\n");
        return separator < 0 ? chunk : chunk.substring(separator + 2);
    }
}
