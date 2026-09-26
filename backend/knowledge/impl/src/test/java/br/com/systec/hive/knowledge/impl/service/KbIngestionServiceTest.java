package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbDocumentStatus;
import br.com.systec.hive.knowledge.api.domain.KbSourceType;
import br.com.systec.hive.knowledge.api.port.KbEmbeddingPort;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.fake.PrefixAwareEmbeddingModel;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ingestion is the document side of the convention, so the assertion is on the text that reached
 * the provider — a spy on the port would only prove the method was called, which is exactly the
 * check that let this run wrong for months.
 */
@ExtendWith(MockitoExtension.class)
class KbIngestionServiceTest {

    private static final String NOMIC = "nomic-embed-text";
    private static final String OPENAI = "text-embedding-3-small";
    private static final String CONTENT = "reinicio do servico de busca do indice vetorial";

    @Mock
    private KbDocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KbEmbeddingStore embeddingStore;

    @Mock
    private KbEmbeddingModelFactory modelFactory;

    @TempDir
    Path storageDir;

    private UUID tenantId;
    private UUID kbId;
    private UUID docId;
    private String fileName = "runbook.txt";
    private PrefixAwareEmbeddingModel model;
    private Executor sameThread;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        docId = UUID.randomUUID();
        fileName = "runbook.txt";
        sameThread = Runnable::run;
    }

    private void wireModel(String modelName) {
        model = new PrefixAwareEmbeddingModel(modelName, KbEmbeddingService.dimensionOf(modelName));
        when(modelFactory.create(any(), any())).thenReturn(model);
    }

    private KbIngestionService service() {
        KbEmbeddingPort embeddingPort = new KbEmbeddingService(modelFactory);
        return new KbIngestionService(documentRepository, knowledgeBaseRepository, embeddingStore,
                embeddingPort, sameThread, storageDir.toString());
    }

    private void givenDocumentAndBase(String modelName, int dimension) throws Exception {
        KbDocumentEntity doc = new KbDocumentEntity();
        doc.setId(docId);
        doc.setTenantId(tenantId);
        doc.setKbId(kbId);
        doc.setSourceType(KbSourceType.UPLOAD.name());
        doc.setFileName(fileName);
        doc.setStatus(KbDocumentStatus.PENDING.name());

        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(kbId);
        kb.setTenantId(tenantId);
        kb.setName("Runbook");
        kb.setEmbeddingModel(modelName);
        kb.setDimension(dimension);

        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.of(doc));
        when(knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId)).thenReturn(Optional.of(kb));

        Path dir = storageDir.resolve(tenantId.toString());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(docId + ".bin"), CONTENT);
    }

    @Test
    void when_ingest_withPrefixRequiringModel_then_providerGetsDocumentPrefixedChunks() throws Exception {
        wireModel(NOMIC);
        givenDocumentAndBase(NOMIC, KbEmbeddingService.dimensionOf(NOMIC));

        service().ingest(docId, tenantId);

        assertThat(model.seenTexts()).isNotEmpty();
        assertThat(model.seenTexts())
                .as("every chunk must reach the model as a document")
                .allSatisfy(text -> assertThat(text).startsWith(PrefixAwareEmbeddingModel.DOCUMENT_PREFIX));
        assertThat(model.seenTexts().get(0))
                .contains(CONTENT)
                .doesNotStartWith(PrefixAwareEmbeddingModel.QUERY_PREFIX);
    }

    @Test
    void when_ingest_withPrefixRequiringModel_then_theVectorStoredIsTheOneTheProviderReturned() throws Exception {
        wireModel(NOMIC);
        givenDocumentAndBase(NOMIC, KbEmbeddingService.dimensionOf(NOMIC));

        service().ingest(docId, tenantId);

        float[] expected = model.vectorFor(model.seenTexts().get(0));
        ArgumentCaptor<float[]> vector = ArgumentCaptor.forClass(float[].class);
        verify(embeddingStore).insert(eq(tenantId), eq(kbId), eq(docId), anyInt(), anyString(), vector.capture(),
                anyInt(), anyString());
        assertThat(vector.getValue()).containsExactly(expected);
    }

    /** The marker is what makes a half-failed reindex visible; see KbEmbeddingStore.metadataJson. */
    @Test
    void when_ingest_then_everyStoredRowCarriesTheConventionThatWroteIt() throws Exception {
        wireModel(NOMIC);
        givenDocumentAndBase(NOMIC, KbEmbeddingService.dimensionOf(NOMIC));

        service().ingest(docId, tenantId);

        verify(embeddingStore, atLeastOnce())
                .insert(eq(tenantId), eq(kbId), eq(docId), anyInt(), anyString(), any(), anyInt(),
                        eq("nomic-task-prefix"));
    }

    @Test
    void when_ingest_withOpenAiModel_then_theMarkerIsPlain() throws Exception {
        wireModel(OPENAI);
        givenDocumentAndBase(OPENAI, KbEmbeddingService.dimensionOf(OPENAI));

        service().ingest(docId, tenantId);

        verify(embeddingStore, atLeastOnce())
                .insert(eq(tenantId), eq(kbId), eq(docId), anyInt(), anyString(), any(), anyInt(),
                        eq(EmbeddingTaskConvention.PLAIN));
    }

    @Test
    void when_metadataIsBuilt_then_theJsonCarriesTheConvention() {
        UUID docId = UUID.randomUUID();

        String json = KbEmbeddingStore.metadataJson(tenantId, kbId, docId, 3, "nomic-task-prefix");

        assertThat(json)
                .contains("\"tenant_id\":\"" + tenantId + "\"")
                .contains("\"kb_id\":\"" + kbId + "\"")
                .contains("\"doc_id\":\"" + docId + "\"")
                .contains("\"chunk_index\":3")
                .contains("\"convention\":\"nomic-task-prefix\"");
    }

    @Test
    void when_metadataIsBuiltWithoutConvention_then_theValueIsEmptyNotNull() {
        assertThat(KbEmbeddingStore.metadataJson(tenantId, kbId, docId, 0, null))
                .contains("\"convention\":\"\"");
    }

    @Test
    void when_ingestWithOpenAiModel_then_providerGetsRawChunks() throws Exception {
        wireModel(OPENAI);
        givenDocumentAndBase(OPENAI, KbEmbeddingService.dimensionOf(OPENAI));

        service().ingest(docId, tenantId);

        assertThat(model.seenTexts()).isNotEmpty();
        assertThat(model.seenTexts())
                .allSatisfy(text -> assertThat(text)
                        .doesNotStartWith(PrefixAwareEmbeddingModel.DOCUMENT_PREFIX)
                        .doesNotStartWith(PrefixAwareEmbeddingModel.QUERY_PREFIX))
                .anySatisfy(text -> assertThat(text).contains("servico"));
    }

    @Test
    void when_documentIsMissing_then_nothingIsEmbedded() {
        when(documentRepository.findByIdAndTenantId(docId, tenantId)).thenReturn(Optional.empty());
        KbEmbeddingModelFactory unusedFactory = mock(KbEmbeddingModelFactory.class);

        KbIngestionService service = new KbIngestionService(documentRepository, knowledgeBaseRepository,
                embeddingStore, new KbEmbeddingService(unusedFactory), sameThread, storageDir.toString());

        service.ingest(docId, tenantId);

        verify(embeddingStore, never()).insert(any(), any(), any(), anyInt(), anyString(), any(), anyInt(),
                any());
    }

    // ------------------------------------------------------------------ chunks with context
    //
    // Everything below asserts on the string handed to insert(...) — the chunk as stored — because
    // the bug this file now guards was never visible from the provider call or the row count. The
    // old code produced a correct number of rows, with the correct vector, from chunks that began
    // and ended mid-sentence and carried no title. Every assertion below fails against it.

    /** The cover line: what a chunk has to know to be understood at all. */
    private static final String TITLE = "GUIA PRATICO: MENTE MAGRA";

    private static final String FILE_NAME = "guia_pratico_mente_magra_sos_v3.pdf";

    /**
     * Writes a document long enough to be cut many times, as real prose so that sentence and word
     * boundaries are meaningful. Each sentence is a distinct, self-contained statement.
     */
    private String longDocument(int sentences) {
        StringBuilder sb = new StringBuilder(TITLE).append("\n\n");
        for (int i = 0; i < sentences; i++) {
            sb.append("A etapa ").append(i)
                    .append(" descreve como o operador reinicia o indice vetorial com seguranca. ")
                    .append("Os passos foram validados em producao. ");
        }
        return sb.toString();
    }

    private void givenDocumentWithContent(String modelName, int dimension, String content) throws Exception {
        fileName = FILE_NAME;
        givenDocumentAndBase(modelName, dimension);
        Files.writeString(storageDir.resolve(tenantId.toString()).resolve(docId + ".bin"), content);
    }

    private List<String> captureStoredChunks() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(embeddingStore, atLeastOnce())
                .insert(eq(tenantId), eq(kbId), eq(docId), anyInt(), text.capture(), any(), anyInt(), anyString());
        return text.getAllValues();
    }

    /** The assertion the spec asks for: the stored chunk carries its title. */
    @Test
    void when_ingest_aLongDocument_then_everyStoredChunkStartsWithTheDocumentTitle() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        List<String> chunks = captureStoredChunks();
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks)
                .as("a segment with no subject is the hallucination risk the RAG tutorial names")
                .allSatisfy(chunk -> assertThat(chunk).startsWith(TITLE));
    }

    /**
     * The file name is still there — the citation resolves against it — but it is no longer the
     * only thing the chunk says about itself. Guards against "fix the title" being implemented as
     * "replace the file name".
     */
    @Test
    void when_ingest_aLongDocument_then_theFileNameIsStillPresentUnderTheTitle() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        assertThat(captureStoredChunks())
                .allSatisfy(chunk -> assertThat(chunk).contains(FILE_NAME));
    }

    /**
     * The boundary assertion. Every stored chunk must end where a sentence ends, because the
     * official hierarchy only closes a segment at the coarsest unit that fits — a paragraph, a
     * line, a sentence, and a word only when nothing above it fits. The old whitespace splitter had
     * no such notion and cut wherever the character budget ran out, which is the mid-sentence
     * fragment quoted in the spec.
     */
    @Test
    void when_ingest_aLongDocument_then_noStoredChunkEndsMidSentence() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        List<String> chunks = captureStoredChunks();
        assertThat(chunks).allSatisfy(chunk -> assertThat(bodyOf(chunk))
                .as("a chunk must end on a sentence boundary, not wherever the budget ran out")
                .endsWith("."));
    }

    /**
     * And the narrower form of the same property: the last word of a chunk is a whole word of the
     * document. The word splitter is the last level of the hierarchy, so this only fails for a
     * single token longer than the whole budget.
     */
    @Test
    void when_ingest_aLongDocument_then_noStoredChunkEndsMidWord() throws Exception {
        String content = longDocument(200);
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), content);

        service().ingest(docId, tenantId);

        Set<String> sourceWords = Arrays.stream(content.split("\\s+")).collect(Collectors.toSet());
        assertThat(captureStoredChunks())
                .allSatisfy(chunk -> {
                    String[] words = bodyOf(chunk).split("\\s+");
                    assertThat(sourceWords)
                            .as("chunk ends with a fragment, not a word of the document")
                            .contains(words[words.length - 1]);
                });
    }

    /**
     * The unit assertion. {@code estimateTokens} is a real byte-pair count, and 512 is what the
     * budget is enforced in. Measured against the old code this is the assertion that discriminates:
     * a 2048-character chunk of Portuguese prose is ~550 real tokens, so the old splitter's "512"
     * was 512 characters of something else entirely.
     *
     * <p>There is deliberately no companion assertion on character length here. It was written
     * first, and it was worthless: the old chunks also came in under 2048 characters, so it passed
     * against the bug it was meant to catch. The unit is proved in
     * {@link KbTokenCountEstimatorTest}, and the budget is proved here.
     */
    @Test
    void when_ingest_aLongDocument_then_everyChunkFitsTheTokenBudget() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        KbDocumentChunker chunker = new KbDocumentChunker();
        assertThat(captureStoredChunks())
                .as("the stored chunk is what the embedding model sees, header included")
                .allSatisfy(chunk -> assertThat(chunker.estimateTokens(chunk))
                        .isLessThanOrEqualTo(KbDocumentChunker.MAX_SEGMENT_TOKENS));
    }

    /** Segments overlap by whole sentences, so no sentence is lost at a boundary. */
    @Test
    void when_ingest_aLongDocument_then_consecutiveChunksShareTheBoundarySentences() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        List<String> chunks = captureStoredChunks();
        assertThat(chunks).hasSizeGreaterThan(1);
        String firstTail = lastSentenceOf(bodyOf(chunks.get(0)));
        assertThat(bodyOf(chunks.get(1)))
                .as("the overlap the 50-token budget asks for has to actually be there")
                .contains(firstTail);
    }

    /** Everything the old splitter guaranteed, still guaranteed. */
    @Test
    void when_ingest_aLongDocument_then_chunkIndexesAreContiguousFromZero() throws Exception {
        wireModel(OPENAI);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), longDocument(200));

        service().ingest(docId, tenantId);

        ArgumentCaptor<Integer> index = ArgumentCaptor.forClass(Integer.class);
        verify(embeddingStore, atLeastOnce())
                .insert(eq(tenantId), eq(kbId), eq(docId), index.capture(), anyString(), any(), anyInt(),
                        anyString());
        assertThat(index.getAllValues()).containsExactlyElementsOf(
                IntStream.range(0, index.getAllValues().size()).boxed().toList());
    }

    /** Blank input still fails the document rather than storing one empty chunk. */
    @Test
    void when_ingest_withBlankText_then_theDocumentFails() throws Exception {
        KbEmbeddingModelFactory unusedFactory = mock(KbEmbeddingModelFactory.class);
        givenDocumentWithContent(OPENAI, KbEmbeddingService.dimensionOf(OPENAI), "   \n\n  ");

        new KbIngestionService(documentRepository, knowledgeBaseRepository, embeddingStore,
                new KbEmbeddingService(unusedFactory), sameThread, storageDir.toString())
                .ingest(docId, tenantId);

        verify(embeddingStore, never()).insert(any(), any(), any(), anyInt(), anyString(), any(), anyInt(),
                any());
    }

    private static String bodyOf(String chunk) {
        int separator = chunk.indexOf("\n\n");
        return separator < 0 ? chunk : chunk.substring(separator + 2);
    }

    private static String lastSentenceOf(String body) {
        String[] sentences = body.split("(?<=\\.)\\s+");
        return sentences[sentences.length - 1];
    }
}
