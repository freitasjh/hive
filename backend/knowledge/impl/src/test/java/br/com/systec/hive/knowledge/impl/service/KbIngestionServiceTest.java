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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

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
    private PrefixAwareEmbeddingModel model;
    private Executor sameThread;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        kbId = UUID.randomUUID();
        docId = UUID.randomUUID();
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
        doc.setFileName("runbook.txt");
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
}
