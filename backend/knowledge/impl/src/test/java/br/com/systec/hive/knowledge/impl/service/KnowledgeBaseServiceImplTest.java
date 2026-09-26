package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.KbStatus;
import br.com.systec.hive.knowledge.api.domain.KnowledgeBase;
import br.com.systec.hive.knowledge.api.exceptions.KbRateLimitException;
import br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException;
import br.com.systec.hive.knowledge.api.port.KbRetrievalPort;
import br.com.systec.hive.knowledge.api.service.AgentKbLinkService;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.mapper.KnowledgeBaseMapperDomain;
import br.com.systec.hive.knowledge.impl.repository.KbDocumentRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseRepository repository;

    @Mock
    private KbDocumentRepository documentRepository;

    @Spy
    private KnowledgeBaseMapperDomain mapper = Mappers.getMapper(KnowledgeBaseMapperDomain.class);

    @Mock
    private AgentKbLinkService linkService;

    @Mock
    private KbIngestionService ingestionService;

    @Mock
    private KbEmbeddingStore embeddingStore;

    @Mock
    private KbRetrievalPort retrievalPort;

    @Mock
    private KbIngestRateLimiter rateLimiter;

    @InjectMocks
    private KnowledgeBaseServiceImpl service;

    private KnowledgeBase newKb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(UUID.randomUUID());
        kb.setName("Central de Ajuda");
        kb.setDescription("docs");
        return kb;
    }

    @Test
    void when_create_with_validKb_then_success() {
        KnowledgeBase kb = newKb();
        when(repository.save(any(KnowledgeBaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase saved = service.create(kb);

        assertThat(saved.getSlug()).isEqualTo("central-de-ajuda");
        assertThat(saved.getDimension()).isEqualTo(1536);
        assertThat(saved.getStatus()).isEqualTo(KbStatus.ACTIVE);
        verify(repository).save(any(KnowledgeBaseEntity.class));
    }

    @Test
    void when_create_with_duplicateSlug_then_conflict() {
        KnowledgeBase kb = newKb();
        when(repository.save(any(KnowledgeBaseEntity.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(kb))
                .hasMessageContaining("slug already exists");
    }

    @Test
    void when_create_with_nomicModel_then_dimension768() {
        KnowledgeBase kb = newKb();
        kb.setEmbeddingModel("nomic-embed-text");
        kb.setDimension(0);
        when(repository.save(any(KnowledgeBaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeBase saved = service.create(kb);

        assertThat(saved.getDimension()).isEqualTo(768);
        assertThat(saved.getEmbeddingModel()).isEqualTo("nomic-embed-text");
        verify(repository).save(any(KnowledgeBaseEntity.class));
    }

    @Test
    void when_create_with_badDimension_then_throws() {
        KnowledgeBase kb = newKb();
        kb.setEmbeddingModel("custom");
        kb.setDimension(1024);

        assertThatThrownBy(() -> service.create(kb)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_findById_with_otherTenant_then_notFound() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id, tenant))
                .isInstanceOf(KnowledgeBaseNotFoundException.class);
    }

    @Test
    void when_list_with_query_then_searchRepo() {
        UUID tenant = UUID.randomUUID();
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenant);
        entity.setName("Ajuda");
        entity.setSlug("ajuda");
        entity.setEmbeddingModel("text-embedding-3-small");
        entity.setDimension(1536);
        entity.setStatus("ACTIVE");
        PageRequest pageable = PageRequest.of(0, 20);
        when(repository.findByTenantIdWithSearch(tenant, "aju", pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<KnowledgeBase> page = service.list(tenant, pageable, "aju");

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getSlug()).isEqualTo("ajuda");
    }

    @Test
    void when_delete_with_linkedAgents_then_conflict() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(entity));
        when(linkService.agentsUsingKb(id, tenant)).thenReturn(List.of("agent-1"));

        assertThatThrownBy(() -> service.delete(id, tenant, false))
                .hasMessageContaining("agent-1");
    }

    @Test
    void when_reindex_with_validKb_then_checksRateLimit() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        when(repository.findByIdAndTenantId(id, tenant)).thenReturn(Optional.of(entity));
        when(documentRepository.findByKbIdAndTenantId(id, tenant)).thenReturn(List.of());

        service.reindex(id, tenant);

        verify(rateLimiter).check(tenant);
        verify(embeddingStore).removeByKb(tenant, id);
    }

    @Test
    void when_reindex_with_rateLimitExceeded_then_throws() {
        UUID id = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new KbRateLimitException()).when(rateLimiter).check(tenant);

        assertThatThrownBy(() -> service.reindex(id, tenant))
                .isInstanceOf(KbRateLimitException.class);
    }
}
