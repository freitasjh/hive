package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbDocument;
import br.com.systec.hive.knowledge.api.exceptions.KbDuplicateDocumentException;
import br.com.systec.hive.knowledge.impl.entity.KbDocumentEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.mapper.KnowledgeBaseMapperDomain;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbDocumentServiceImplTest {

    @Mock
    private KbDocumentRepository documentRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Mock
    private KnowledgeBaseMapperDomain mapper;

    @Mock
    private KbIngestionService ingestionService;

    @Mock
    private KbEmbeddingStore embeddingStore;

    @Mock
    private KbIngestRateLimiter rateLimiter;

    @InjectMocks
    private KbDocumentServiceImpl service;

    @Test
    void when_upload_with_sameContentSameKb_then_returnsExisting() {
        UUID kbId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KbDocumentEntity existing = new KbDocumentEntity();
        existing.setKbId(kbId);
        KbDocument domain = new KbDocument();
        when(knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId))
                .thenReturn(Optional.of(new KnowledgeBaseEntity()));
        when(documentRepository.findByTenantIdAndSha256(any(), any()))
                .thenReturn(Optional.of(existing));
        when(mapper.toDomain(existing)).thenReturn(domain);

        KbDocument result = service.upload(kbId, tenantId, "a.txt", "text/plain",
                new ByteArrayInputStream("hello".getBytes()));

        assertThat(result).isSameAs(domain);
    }

    @Test
    void when_upload_with_sameContentOtherKb_then_throwsConflict() {
        UUID kbId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        KbDocumentEntity existing = new KbDocumentEntity();
        existing.setKbId(UUID.randomUUID());
        when(knowledgeBaseRepository.findByIdAndTenantId(kbId, tenantId))
                .thenReturn(Optional.of(new KnowledgeBaseEntity()));
        when(documentRepository.findByTenantIdAndSha256(any(), any()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.upload(kbId, tenantId, "a.txt", "text/plain",
                new ByteArrayInputStream("hello".getBytes())))
                .isInstanceOf(KbDuplicateDocumentException.class);
    }
}
