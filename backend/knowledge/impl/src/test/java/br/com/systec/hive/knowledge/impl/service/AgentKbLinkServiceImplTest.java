package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbTooManyLinksException;
import br.com.systec.hive.knowledge.impl.entity.AgentKbEntity;
import br.com.systec.hive.knowledge.impl.entity.KnowledgeBaseEntity;
import br.com.systec.hive.knowledge.impl.repository.AgentKbRepository;
import br.com.systec.hive.knowledge.impl.repository.KnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentKbLinkServiceImplTest {

    @Mock
    private AgentKbRepository linkRepository;

    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @InjectMocks
    private AgentKbLinkServiceImpl service;

    @Test
    void when_link_with_fiveExisting_then_throws() {
        UUID agent = UUID.randomUUID();
        UUID kb = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(kb);
        when(knowledgeBaseRepository.findByIdAndTenantId(kb, tenant)).thenReturn(Optional.of(entity));
        when(linkRepository.existsByAgentIdAndKbIdAndTenantId(agent, kb, tenant)).thenReturn(false);
        when(linkRepository.countByAgentIdAndTenantId(agent, tenant)).thenReturn(5L);

        assertThatThrownBy(() -> service.link(agent, kb, tenant))
                .isInstanceOf(KbTooManyLinksException.class);
    }

    @Test
    void when_replaceAll_with_sixKbs_then_throws() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        List<UUID> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(UUID.randomUUID());
        }
        assertThatThrownBy(() -> service.replaceAll(agent, tenant, six))
                .isInstanceOf(KbTooManyLinksException.class);
    }

    @Test
    void when_replaceAll_with_unknownKb_then_notFound() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID());
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(tenant, ids)).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceAll(agent, tenant, ids))
                .isInstanceOf(br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException.class);
    }

    @Test
    void when_linkedKbNames_with_links_then_sortedNames() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();
        when(linkRepository.findKbIdsByAgent(agent, tenant)).thenReturn(List.of(kb1, kb2));
        KnowledgeBaseEntity a = new KnowledgeBaseEntity();
        a.setId(kb1);
        a.setName("Zeta");
        KnowledgeBaseEntity b = new KnowledgeBaseEntity();
        b.setId(kb2);
        b.setName("Alfa");
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(tenant, List.of(kb1, kb2)))
                .thenReturn(List.of(a, b));

        assertThat(service.linkedKbNames(agent, tenant)).containsExactly("Alfa", "Zeta");
    }

    // ================= RODADA 2 — escrita via porta (replaceLinks) =================

    @Test
    void when_replaceLinks_with_kbFromAnotherTenant_then_throwsNotFoundAndTouchesNoLink() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID foreignKb = UUID.randomUUID();
        // A base existe, mas em outro tenant: findByTenantIdAndIdIn não a devolve.
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(tenant, List.of(foreignKb))).thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceLinks(agent, tenant, List.of(foreignKb)))
                .isInstanceOf(br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException.class);

        // Nenhum delete e nenhum insert: a validação de tenant roda antes de tocar em qualquer vínculo,
        // então a falha não pode deixar o agente com um conjunto parcial.
        verifyNoInteractions(linkRepository);
    }

    @Test
    void when_replaceLinks_with_mixedValidAndForeignKb_then_leavesPreviousLinksUntouched() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID ownKb = UUID.randomUUID();
        UUID foreignKb = UUID.randomUUID();
        KnowledgeBaseEntity own = new KnowledgeBaseEntity();
        own.setId(ownKb);
        // Uma das duas pertence ao tenant; como a comparação é por tamanho, o conjunto não fecha.
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(tenant, List.of(ownKb, foreignKb)))
                .thenReturn(List.of(own));

        assertThatThrownBy(() -> service.replaceLinks(agent, tenant, List.of(ownKb, foreignKb)))
                .isInstanceOf(br.com.systec.hive.knowledge.api.exceptions.KnowledgeBaseNotFoundException.class);

        verifyNoInteractions(linkRepository);
    }

    @Test
    void when_replaceLinks_with_sameSet_then_deletesNothingAndInsertsNothing() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();
        givenTenantOwns(tenant, List.of(kb1, kb2), kb1, kb2);
        when(linkRepository.findByAgentIdAndTenantId(agent, tenant)).thenReturn(List.of(link(agent, kb1, tenant)));
        when(linkRepository.existsByAgentIdAndKbIdAndTenantId(agent, kb1, tenant)).thenReturn(true);
        when(linkRepository.existsByAgentIdAndKbIdAndTenantId(agent, kb2, tenant)).thenReturn(false);

        service.replaceLinks(agent, tenant, List.of(kb1, kb2));

        verify(linkRepository, never()).delete(any(AgentKbEntity.class));
        ArgumentCaptor<AgentKbEntity> saved = ArgumentCaptor.forClass(AgentKbEntity.class);
        verify(linkRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getKbId()).isEqualTo(kb2);
    }

    @Test
    void when_replaceLinks_withRemovedKb_then_deletesTheStaleLink() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        UUID dropped = UUID.randomUUID();
        givenTenantOwns(tenant, List.of(kept), kept);
        when(linkRepository.findByAgentIdAndTenantId(agent, tenant))
                .thenReturn(List.of(link(agent, kept, tenant), link(agent, dropped, tenant)));
        when(linkRepository.existsByAgentIdAndKbIdAndTenantId(agent, kept, tenant)).thenReturn(true);

        service.replaceLinks(agent, tenant, List.of(kept));

        ArgumentCaptor<AgentKbEntity> deleted = ArgumentCaptor.forClass(AgentKbEntity.class);
        verify(linkRepository, times(1)).delete(deleted.capture());
        assertThat(deleted.getValue().getKbId()).isEqualTo(dropped);
        verify(linkRepository, never()).save(any(AgentKbEntity.class));
    }

    @Test
    void when_replaceLinks_withEmptyList_then_unlinksEverything() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        when(linkRepository.findByAgentIdAndTenantId(agent, tenant))
                .thenReturn(List.of(link(agent, UUID.randomUUID(), tenant)));

        service.replaceLinks(agent, tenant, List.of());

        verify(linkRepository, times(1)).delete(any(AgentKbEntity.class));
        verify(linkRepository, never()).save(any(AgentKbEntity.class));
    }

    @Test
    void when_replaceLinks_withDuplicatedIds_then_deduplicatesBeforeCountingTheLimit() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID kb = UUID.randomUUID();
        // 7 entradas, 1 id distinto: o limite é de bases, não de entradas no array.
        List<UUID> padded = new ArrayList<>(List.of(kb));
        padded.addAll(List.of(kb, kb, kb, kb, kb, kb));
        givenTenantOwns(tenant, List.of(kb), kb);
        when(linkRepository.findByAgentIdAndTenantId(agent, tenant)).thenReturn(List.of());
        when(linkRepository.existsByAgentIdAndKbIdAndTenantId(agent, kb, tenant)).thenReturn(false);

        service.replaceLinks(agent, tenant, padded);

        verify(linkRepository, times(1)).save(any(AgentKbEntity.class));
    }

    @Test
    void when_replaceLinks_withSixDistinct_then_ptBrMessageNamesTheRule() {
        UUID agent = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        List<UUID> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(UUID.randomUUID());
        }

        assertThatThrownBy(() -> service.replaceLinks(agent, tenant, six))
                .isInstanceOf(KbTooManyLinksException.class)
                .hasMessageContaining("Máximo de 5 bases de conhecimento por agente")
                .hasMessageContaining("Remova um vínculo");
    }

    private void givenTenantOwns(UUID tenant, List<UUID> queried, UUID... owned) {
        List<KnowledgeBaseEntity> entities = new ArrayList<>();
        for (UUID id : owned) {
            KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
            entity.setId(id);
            entity.setName("kb-" + id);
            entities.add(entity);
        }
        when(knowledgeBaseRepository.findByTenantIdAndIdIn(tenant, queried)).thenReturn(entities);
    }

    private static AgentKbEntity link(UUID agentId, UUID kbId, UUID tenantId) {
        AgentKbEntity entity = new AgentKbEntity();
        entity.setAgentId(agentId);
        entity.setKbId(kbId);
        entity.setTenantId(tenantId);
        return entity;
    }
}
