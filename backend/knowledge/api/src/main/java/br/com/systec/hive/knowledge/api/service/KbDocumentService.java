package br.com.systec.hive.knowledge.api.service;

import br.com.systec.hive.knowledge.api.domain.KbDocument;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public interface KbDocumentService {

    KbDocument upload(UUID kbId, UUID tenantId, String originalFilename, String contentType, InputStream content);

    List<KbDocument> listByKb(UUID kbId, UUID tenantId);

    KbDocument findById(UUID docId, UUID tenantId);

    void delete(UUID docId, UUID tenantId);
}
