package br.com.systec.hive.agent.impl.mapper;

import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.impl.entity.ChatMessageEntity;
import br.com.systec.hive.agent.impl.entity.ChatSessionEntity;
import br.com.systec.hive.provider.api.ChatRole;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageMapperDomainTest {

    private final ChatMessageMapperDomain mapper = Mappers.getMapper(ChatMessageMapperDomain.class);

    @Test
    void when_toDomain_with_entity_then_mapsSessionIdAndRole() {
        ChatSessionEntity session = new ChatSessionEntity();
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setSession(session);
        entity.setRole(ChatRole.USER);
        entity.setContent("hello");
        entity.setTenantId(UUID.randomUUID());
        entity.setCreateAt(LocalDateTime.now());

        ChatMessage domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.getSessionId()).isEqualTo(sessionId);
        assertThat(domain.getRole()).isEqualTo(ChatRole.USER);
        assertThat(domain.getContent()).isEqualTo("hello");
        assertThat(domain.getTenantId()).isEqualTo(entity.getTenantId());
        assertThat(domain.getCreateAt()).isEqualTo(entity.getCreateAt());
    }

    @Test
    void when_toDomain_with_assistantRole_then_mapsCorrectly() {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(UUID.randomUUID());

        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setSession(session);
        entity.setRole(ChatRole.ASSISTANT);
        entity.setContent("assistant content");

        ChatMessage domain = mapper.toDomain(entity);

        assertThat(domain.getRole()).isEqualTo(ChatRole.ASSISTANT);
    }

    @Test
    void when_toEntity_with_domain_then_ignoresAutoFields() {
        ChatMessage domain = new ChatMessage();
        domain.setId(UUID.randomUUID());
        domain.setSessionId(UUID.randomUUID());
        domain.setRole(ChatRole.USER);
        domain.setContent("user content");
        domain.setTenantId(UUID.randomUUID());
        domain.setCreateAt(LocalDateTime.now());

        ChatMessageEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getRole()).isEqualTo(ChatRole.USER);
        assertThat(entity.getContent()).isEqualTo("user content");
        assertThat(entity.getSession()).isNull();
        assertThat(entity.getCreateAt()).isNull();
        assertThat(entity.getTenantId()).isNull();
        assertThat(entity.getUpdateAt()).isNull();
    }

    @Test
    void when_toEntity_with_userRole_then_mapsRole() {
        ChatMessage domain = new ChatMessage();
        domain.setRole(ChatRole.USER);
        domain.setContent("test");

        ChatMessageEntity entity = mapper.toEntity(domain);

        assertThat(entity.getRole()).isEqualTo(ChatRole.USER);
    }
}
