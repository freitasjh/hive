package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.commons.impl.http.PinnedHttpClient;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.knowledge.api.domain.KbDocumentStatus;
import br.com.systec.hive.knowledge.api.domain.KbSourceType;
import br.com.systec.hive.knowledge.api.port.KbEmbeddingPort;
import br.com.systec.hive.knowledge.api.port.KbVectorStore;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import br.com.systec.hive.knowledge.impl.security.KbUrlGuard;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class KbIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KbIngestionService.class);
    private static final long PARSE_TIMEOUT_SECONDS = 30L;

    private final KbDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KbVectorStore vectorStore;
    private final KbEmbeddingPort embeddingPort;
    private final Executor parseExecutor;
    private final String storageDir;

    /**
     * Built here rather than injected, and that is deliberate.
     *
     * <p>An earlier version of this class took a second constructor taking a
     * {@link KbDocumentChunker}, to satisfy the project's constructor-injection convention. It
     * broke every Spring context: with two public constructors and no {@code @Autowired} among
     * them, {@code KbIngestionService} is instantiated through the no-arg constructor, which does
     * not exist — {@code NoSuchMethodException: KbIngestionService.<init>()}, and with it the whole
     * application. Unit tests never saw it, because they construct the class directly; the
     * integration suite did, immediately.
     *
     * <p>Injecting it would also mean publishing a {@code KbDocumentChunker} bean for a class that
     * is stateless, holds no configuration and delegates to a shared immutable tokenizer. The
     * convention exists so collaborators can be substituted in tests, and nothing here needs to be.
     */
    private final KbDocumentChunker chunker = new KbDocumentChunker();

    public KbIngestionService(KbDocumentRepository documentRepository,
                              KnowledgeBaseRepository knowledgeBaseRepository,
                              KbVectorStore vectorStore,
                              KbEmbeddingPort embeddingPort,
                              @Qualifier("kbParseExecutor") Executor parseExecutor,
                              @Value("${hive.kb.storage-dir:${java.io.tmpdir}/hive-kb}") String storageDir) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.vectorStore = vectorStore;
        this.embeddingPort = embeddingPort;
        this.parseExecutor = parseExecutor;
        this.storageDir = storageDir;
    }

    @Async("kbIngestExecutor")
    public void ingestAsync(UUID docId, UUID tenantId) {
        try {
            TenantContext.runWithTenant(tenantId, (Callable<Void>) () -> {
                ingest(docId, tenantId);
                return null;
            });
        } catch (Exception e) {
            log.error("kb ingest async failed docId={} tenantId={}", docId, tenantId, e);
            markFailed(docId, tenantId, "ingestion failed");
        }
    }

    void ingest(UUID docId, UUID tenantId) {
        KbDocumentEntity doc = documentRepository.findByIdAndTenantId(docId, tenantId).orElse(null);
        if (doc == null) {
            log.warn("kb ingest doc not found docId={} tenantId={}", docId, tenantId);
            return;
        }
        setStatus(doc, KbDocumentStatus.INDEXING.name(), null);
        try {
            KnowledgeBaseEntity kb = knowledgeBaseRepository.findByIdAndTenantId(doc.getKbId(), tenantId)
                    .orElseThrow(() -> new IllegalStateException("knowledge base not found"));
            byte[] bytes = loadBytes(doc, tenantId);
            ParsedDocument parsed = parseWithTimeout(bytes, doc.getFileName());
            String text = KbFileValidator.truncateText(parsed.text());
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("no extractable text (scanned-image PDFs unsupported)");
            }
            vectorStore.removeByDoc(tenantId, docId);
            // The title is resolved against the truncated text: a cut in the middle of the cover
            // page must not turn into a chunk header that ends mid-word.
            List<String> chunks = chunker.chunk(
                    KbDocumentTitle.resolve(parsed.metadata(), text, doc.getFileName()), doc.getFileName(), text);
            int index = 0;
            int totalTokens = 0;
            // Written on every row: ingestion is async, so a reindex that fails on some documents
            // leaves a base mixing two conventions with no other trace. See KbVectorStore.
            String convention = EmbeddingTaskConvention.conventionId(kb.getEmbeddingModel());
            for (String chunk : chunks) {
                float[] vector = embeddingPort.embedDocument(tenantId, kb.getEmbeddingModel(), kb.getDimension(), chunk);
                vectorStore.insert(tenantId, kb.getId(), docId, index, chunk, vector, kb.getDimension(), convention);
                totalTokens += chunker.estimateTokens(chunk);
                index++;
            }
            setStatus(doc, KbDocumentStatus.READY.name(), null);
            // Per document, and therefore on every ingest: the factory's line only fires on a cache
            // miss, so an operator grepping a bad reindex would find nothing there.
            log.info("kb ingest ready docId={} tenantId={} kbId={} chunks={} tokenUsage~={} convention={}",
                    docId, tenantId, kb.getId(), index, totalTokens, convention);
        } catch (Exception e) {
            log.warn("kb ingest failed docId={} tenantId={}: {}", docId, tenantId, e.getMessage());
            markFailed(docId, tenantId, safeError(e));
        }
    }

    public Path storeBytes(UUID tenantId, UUID docId, byte[] bytes) {
        try {
            Path dir = Paths.get(storageDir, tenantId.toString());
            Files.createDirectories(dir);
            Path file = dir.resolve(docId.toString() + ".bin");
            Files.write(file, bytes);
            return file;
        } catch (Exception e) {
            throw new IllegalStateException("kb storage failed", e);
        }
    }

    byte[] loadBytes(KbDocumentEntity doc, UUID tenantId) throws Exception {
        if (doc.getSourceType().equals(KbSourceType.URL.name())) {
            KbUrlGuard.FetchedContent fetched = KbUrlGuard.fetchWithRedirects(
                    doc.getSourceUri(), this::fetchHttp);
            storeBytes(tenantId, doc.getId(), fetched.bytes());
            return fetched.bytes();
        }
        Path file = Paths.get(storageDir, tenantId.toString(), doc.getId().toString() + ".bin");
        return Files.readAllBytes(file);
    }

    /**
     * Fetches a URL over a socket pinned to the pre-validated IP.
     *
     * <p>The TCP/TLS connection targets the {@code pinnedIp} literal directly — the hostname
     * is never re-resolved at connect time, closing the DNS-rebinding/TOCTOU window. The
     * original host is preserved via {@code Host} header + SNI, and the peer address is
     * asserted via {@code socket.getRemoteSocketAddress()} before the body is read
     * (see {@link PinnedHttpClient}).
     */
    KbUrlGuard.RedirectAwareContent fetchHttp(URI uri, InetAddress pinnedIp) throws Exception {
        long start = System.currentTimeMillis();
        PinnedHttpClient.PinnedResponse response = PinnedHttpClient.get(
                uri, pinnedIp, KbUrlGuard.CONNECT_TIMEOUT_MS, KbUrlGuard.TOTAL_TIMEOUT_MS, KbUrlGuard.MAX_BYTES);
        if (System.currentTimeMillis() - start > KbUrlGuard.TOTAL_TIMEOUT_MS) {
            throw new IllegalStateException("fetch total timeout");
        }
        int status = response.status();
        if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            String location = response.headers().getOrDefault("location", List.of())
                    .stream().findFirst().orElse(null);
            if (location == null || location.isBlank()) {
                throw new IllegalStateException("redirect without location");
            }
            return new KbUrlGuard.RedirectAwareContent(new byte[0], null, location);
        }
        KbUrlGuard.requireOkStatus(status);
        String contentType = response.headers().getOrDefault("content-type", List.of())
                .stream().findFirst().orElse(null);
        return new KbUrlGuard.RedirectAwareContent(response.body(), contentType, null);
    }

    /**
     * A parsed document, with the parser's metadata kept.
     *
     * <p>Metadata is the only place a document states its own title (PDF {@code /Title}, DOCX core
     * properties, HTML {@code <title>}); returning text alone threw it away and left the file name
     * as the only thing every chunk could say about itself.
     */
    record ParsedDocument(String text, Metadata metadata) {
    }

    ParsedDocument parseWithTimeout(byte[] bytes, String fileName) throws Exception {
        Future<ParsedDocument> future = CompletableFuture.supplyAsync(() -> {
            ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                Document document = parser.parse(in);
                return new ParsedDocument(document.text(), document.metadata());
            } catch (Exception e) {
                throw new IllegalStateException("parse failed", e);
            }
        }, parseExecutor);
        try {
            return future.get(PARSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new IllegalStateException("parse timeout");
        }
    }

    private void setStatus(KbDocumentEntity doc, String status, String error) {
        doc.setStatus(status);
        doc.setError(error);
        documentRepository.save(doc);
    }

    private void markFailed(UUID docId, UUID tenantId, String error) {
        try {
            TenantContext.runWithTenant(tenantId, (Callable<Void>) () -> {
                documentRepository.findByIdAndTenantId(docId, tenantId)
                        .ifPresent(d -> setStatus(d, KbDocumentStatus.FAILED.name(), error));
                return null;
            });
        } catch (Exception e) {
            log.error("kb markFailed failed docId={} tenantId={}", docId, tenantId, e);
        }
    }

    static String safeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            return "ingestion failed";
        }
        if (msg.length() > 500) {
            return msg.substring(0, 500);
        }
        return msg;
    }
}
