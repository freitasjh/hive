package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KbDocumentStatus;
import br.com.systec.hive.knowledge.api.domain.KbSourceType;
import br.com.systec.hive.knowledge.api.exceptions.KbDocumentNotFoundException;
import br.com.systec.hive.knowledge.api.exceptions.KbDuplicateDocumentException;
import br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException;
import br.com.systec.hive.knowledge.api.service.KbDocumentService;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.mapper.KnowledgeBaseMapperDomain;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import br.com.systec.hive.knowledge.impl.security.KbUrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class KbDocumentServiceImpl implements KbDocumentService {

    private static final Logger log = LoggerFactory.getLogger(KbDocumentServiceImpl.class);

    private final KbDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseMapperDomain mapper;
    private final KbIngestionService ingestionService;
    private final KbEmbeddingStore embeddingStore;
    private final KbIngestRateLimiter rateLimiter;

    public KbDocumentServiceImpl(KbDocumentRepository documentRepository,
                                 KnowledgeBaseRepository knowledgeBaseRepository,
                                 KnowledgeBaseMapperDomain mapper,
                                 KbIngestionService ingestionService,
                                 KbEmbeddingStore embeddingStore,
                                 KbIngestRateLimiter rateLimiter) {
        this.documentRepository = documentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.mapper = mapper;
        this.ingestionService = ingestionService;
        this.embeddingStore = embeddingStore;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional
    public KbDocument upload(UUID kbId, UUID tenantId, String originalFilename, String contentType, InputStream content) {
        knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        rateLimiter.check(tenantId);
        byte[] bytes;
        try {
            bytes = content.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("upload read failed", e);
        }
        KbFileValidator.checkSize(bytes.length);
        KbFileValidator.checkUpload(originalFilename, contentType, bytes);
        String sha256 = KbUrlGuard.sha256Hex(bytes);
        var existing = documentRepository.findByTenantIdAndSha256(tenantId, sha256);
        if (existing.isPresent()) {
            if (existing.get().getKbId().equals(kbId)) {
                return mapper.toDomain(existing.get());
            }
            throw new KbDuplicateDocumentException(existing.get().getKbId());
        }
        KbDocumentEntity entity = new KbDocumentEntity();
        entity.setTenantId(tenantId);
        entity.setKbId(kbId);
        entity.setSourceType(KbSourceType.UPLOAD.name());
        entity.setFileName(originalFilename);
        entity.setSha256(sha256);
        entity.setStatus(KbDocumentStatus.PENDING.name());
        KbDocumentEntity saved = documentRepository.save(entity);
        ingestionService.storeBytes(tenantId, saved.getId(), bytes);
        ingestionService.ingestAsync(saved.getId(), tenantId);
        return mapper.toDomain(saved);
    }

    @Override
    public List<KbDocument> listByKb(UUID kbId, UUID tenantId) {
        knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        return documentRepository.findByKbIdAndTenantId(kbId, tenantId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public KbDocument findById(UUID docId, UUID tenantId) {
        return documentRepository.findByIdAndTenantId(docId, tenantId)
                .map(mapper::toDomain)
                .orElseThrow(KbDocumentNotFoundException::new);
    }

    @Override
    @Transactional
    public void delete(UUID docId, UUID tenantId) {
        KbDocumentEntity entity = documentRepository.findByIdAndTenantId(docId, tenantId)
                .orElseThrow(KbDocumentNotFoundException::new);
        embeddingStore.removeByDoc(tenantId, docId);
        documentRepository.delete(entity);
        log.info("kb document deleted tenantId={} docId={}", tenantId, docId);
    }
}
