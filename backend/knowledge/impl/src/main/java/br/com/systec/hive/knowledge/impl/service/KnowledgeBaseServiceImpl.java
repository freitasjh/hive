package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KbDocumentStatus;
import br.com.systec.hive.knowledge.api.domain.KbSourceType;
import br.com.systec.hive.knowledge.api.domain.KbStatus;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import br.com.systec.hive.knowledge.api.exceptions.KbInUseException;
import br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException;
import br.com.systec.hive.knowledge.api.domain.RagQuerySettings;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.knowledge.api.port.KbVectorStore;
import br.com.systec.hive.knowledge.api.service.AgentKbLinkService;
import br.com.systec.hive.knowledge.api.service.KnowledgeBaseService;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.mapper.KnowledgeBaseMapperDomain;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import br.com.systec.hive.knowledge.impl.security.KbUrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);

    private final KnowledgeBaseRepository repository;
    private final KbDocumentRepository documentRepository;
    private final KnowledgeBaseMapperDomain mapper;
    private final AgentKbLinkService linkService;
    private final KbIngestionService ingestionService;
    private final KbVectorStore vectorStore;
    private final KbRetrievalPort retrievalPort;
    private final KbIngestRateLimiter rateLimiter;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository repository,
                                    KbDocumentRepository documentRepository,
                                    KnowledgeBaseMapperDomain mapper,
                                    AgentKbLinkService linkService,
                                    KbIngestionService ingestionService,
                                    KbVectorStore vectorStore,
                                    KbRetrievalPort retrievalPort,
                                    KbIngestRateLimiter rateLimiter) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.mapper = mapper;
        this.linkService = linkService;
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.retrievalPort = retrievalPort;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional
    public KnowledgeBase create(KnowledgeBase obj) {
        if (obj.getName() == null || obj.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (obj.getEmbeddingModel() == null || obj.getEmbeddingModel().isBlank()) {
            obj.setEmbeddingModel(KbEmbeddingService.OPENAI_DEFAULT_MODEL);
            obj.setDimension(1536);
        } else if (obj.getDimension() == 0) {
            obj.setDimension(KbEmbeddingService.dimensionOf(obj.getEmbeddingModel()));
        }
        if (obj.getDimension() != 1536 && obj.getDimension() != 768) {
            throw new IllegalArgumentException("dimension must be 1536 or 768");
        }
        obj.setSlug(slugify(obj.getName()));
        if (obj.getStatus() == null) {
            obj.setStatus(KbStatus.ACTIVE);
        }
        KnowledgeBaseEntity entity = mapper.toEntity(obj);
        entity.setTenantId(obj.getTenantId());
        try {
            KnowledgeBaseEntity saved = repository.save(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BaseException("knowledge base slug already exists", HttpStatus.CONFLICT);
        }
    }

    @Override
    @Transactional
    public KnowledgeBase update(KnowledgeBase obj) {
        KnowledgeBaseEntity entity = repository.findByIdAndTenantId(obj.getId(), obj.getTenantId())
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        if (obj.getName() != null && !obj.getName().isBlank()) {
            entity.setName(obj.getName());
        }
        if (obj.getDescription() != null) {
            entity.setDescription(obj.getDescription());
        }
        if (obj.getStatus() != null) {
            entity.setStatus(obj.getStatus().name());
        }
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public KnowledgeBase delete(KnowledgeBase obj) {
        delete(obj.getId(), obj.getTenantId(), false);
        return obj;
    }

    @Override
    public Page<KnowledgeBase> list(UUID tenantId, Pageable pageable, String q) {
        Page<KnowledgeBaseEntity> page = (q == null || q.isBlank())
                ? repository.findByTenantId(tenantId, pageable)
                : repository.findByTenantIdWithSearch(tenantId, q, pageable);
        return page.map(mapper::toDomain);
    }

    @Override
    public KnowledgeBase findById(UUID id, UUID tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toDomain)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID tenantId, boolean force) {
        KnowledgeBaseEntity entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        List<String> agents = linkService.agentsUsingKb(id, tenantId);
        if (!agents.isEmpty() && !force) {
            throw new KbInUseException(String.join(",", agents));
        }
        if (!agents.isEmpty()) {
            linkService.unlinkAllFromKb(id, tenantId);
        }
        vectorStore.removeByKb(tenantId, id);
        documentRepository.deleteByKbIdAndTenantId(id, tenantId);
        repository.delete(entity);
        log.info("kb deleted tenantId={} kbId={} force={}", tenantId, id, force);
    }

    @Override
    @Transactional
    public KbDocument addUrlDocument(UUID kbId, UUID tenantId, String url) {
        repository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        rateLimiter.check(tenantId);
        KbUrlGuard.validateInitial(url);
        KbDocumentEntity entity = new KbDocumentEntity();
        entity.setTenantId(tenantId);
        entity.setKbId(kbId);
        entity.setSourceType(KbSourceType.URL.name());
        entity.setSourceUri(url);
        entity.setFileName(url);
        entity.setStatus(KbDocumentStatus.PENDING.name());
        KbDocumentEntity saved = documentRepository.save(entity);
        ingestionService.ingestAsync(saved.getId(), tenantId);
        return mapper.toDomain(saved);
    }

    /**
     * Playground search.
     *
     * <p>States the cut-off as a policy ({@link RagQuerySettings#fromKbConvention()}) rather than as
     * a number: the cut-off belongs to the base's model, and this method no longer knows which model
     * that is beyond the id it was given. Hardcoding it here is what made the on-screen search and
     * the chat disagree about the same base — and stating the policy is what makes the two call
     * sites agree out loud instead of by both happening to pass the same literal.
     */
    @Override
    public List<KbChunk> search(UUID kbId, UUID tenantId, String query, int topK) {
        repository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        return retrievalPort.retrieve(tenantId, List.of(kbId), query, topK,
                RagQuerySettings.fromKbConvention());
    }

    @Override
    @Transactional
    public void reindex(UUID kbId, UUID tenantId) {
        rateLimiter.check(tenantId);
        repository.findByIdAndTenantId(kbId, tenantId)
                .orElseThrow(KnowledgeBaseNotFoundException::new);
        vectorStore.removeByKb(tenantId, kbId);
        List<KbDocumentEntity> docs = documentRepository.findByKbIdAndTenantId(kbId, tenantId);
        for (KbDocumentEntity doc : docs) {
            doc.setStatus(KbDocumentStatus.PENDING.name());
            doc.setError(null);
            documentRepository.save(doc);
            ingestionService.ingestAsync(doc.getId(), tenantId);
        }
    }

    static String slugify(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
