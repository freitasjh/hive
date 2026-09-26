package br.com.systec.hive.knowledge.api.service;

import br.com.systec.hive.commons.api.service.CrudService;
import br.com.systec.hive.knowledge.api.domain.KbChunk;
import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseService extends CrudService<KnowledgeBase> {

    Page<KnowledgeBase> list(UUID tenantId, Pageable pageable, String q);

    KnowledgeBase findById(UUID id, UUID tenantId);

    void delete(UUID id, UUID tenantId, boolean force);

    KbDocument addUrlDocument(UUID kbId, UUID tenantId, String url);

    List<KbChunk> search(UUID kbId, UUID tenantId, String query, int topK);

    void reindex(UUID kbId, UUID tenantId);
}
