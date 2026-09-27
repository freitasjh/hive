package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionMapperDomainTest {

    private final ChatSessionMapperDomain mapper = Mappers.getMapper(ChatSessionMapperDomain.class);

    @Test
    void when_toDomain_with_entity_then_mapsAllFields() {
        ChatSessionEntity entity = new ChatSessionEntity();
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID promptTemplateId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setPromptTemplateId(promptTemplateId);
        entity.setTenantId(tenantId);
        entity.setTitle("Test Title");
        entity.setSummary("summary");
        entity.setCreateAt(LocalDateTime.now());
        entity.setUpdateAt(LocalDateTime.now());

        ChatSession domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(id);
        assertThat(domain.getUserId()).isEqualTo(userId);
        assertThat(domain.getPromptTemplateId()).isEqualTo(promptTemplateId);
        assertThat(domain.getTenantId()).isEqualTo(tenantId);
        assertThat(domain.getTitle()).isEqualTo("Test Title");
        assertThat(domain.getSummary()).isEqualTo("summary");
        assertThat(domain.getCreateAt()).isEqualTo(entity.getCreateAt());
    }

    @Test
    void when_toEntity_with_domain_then_ignoresCreateAtUpdateAtTenantId() {
        ChatSession domain = new ChatSession();
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID promptTemplateId = UUID.randomUUID();
        domain.setId(id);
        domain.setUserId(userId);
        domain.setPromptTemplateId(promptTemplateId);
        domain.setTitle("Domain Title");
        domain.setSummary("domain summary");
        domain.setTenantId(UUID.randomUUID());
        domain.setCreateAt(LocalDateTime.now());

        ChatSessionEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getPromptTemplateId()).isEqualTo(promptTemplateId);
        assertThat(entity.getTitle()).isEqualTo("Domain Title");
        assertThat(entity.getSummary()).isEqualTo("domain summary");
        assertThat(entity.getCreateAt()).isNull();
        assertThat(entity.getUpdateAt()).isNull();
        assertThat(entity.getTenantId()).isNull();
    }

    @Test
    void when_toDomain_with_agentEntity_then_mapsPromptTemplateId() {
        ChatSessionEntity entity = new ChatSessionEntity();
        UUID promptTemplateId = UUID.randomUUID();
        entity.setId(UUID.randomUUID());
        entity.setUserId(UUID.randomUUID());
        entity.setPromptTemplateId(promptTemplateId);
        entity.setTitle("Title");
        entity.setTenantId(UUID.randomUUID());

        ChatSession domain = mapper.toDomain(entity);

        assertThat(domain.getPromptTemplateId()).isEqualTo(promptTemplateId);
    }
}
