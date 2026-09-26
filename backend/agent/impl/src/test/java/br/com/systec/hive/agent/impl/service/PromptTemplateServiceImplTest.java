package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.PromptStatus;
import br.com.systec.hive.agent.api.domain.PromptTemplate;
import br.com.systec.hive.agent.api.exceptions.CodeImmutableException;
import br.com.systec.hive.agent.api.exceptions.InvalidTemplateVariableException;
import br.com.systec.hive.agent.api.exceptions.PromptCodeDuplicateException;
import br.com.systec.hive.agent.api.exceptions.PromptInUseException;
import br.com.systec.hive.agent.api.exceptions.PromptTemplateNotFoundException;
import br.com.systec.hive.agent.impl.entity.PromptTemplateEntity;
import br.com.systec.hive.agent.impl.mapper.PromptTemplateDomainMapper;
import br.com.systec.hive.agent.impl.repository.AgentRepository;
import br.com.systec.hive.agent.impl.repository.PromptTemplateRepository;
import br.com.systec.hive.commons.impl.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PromptTemplateServiceImplTest {

    @Mock
    private PromptTemplateRepository repository;

    @Mock
    private PromptTemplateDomainMapper mapper;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private PromptTemplateServiceImpl service;

    @Test
    void when_createDraft_then_cloneLatestVersion() {
        UUID tenantId = UUID.randomUUID();
        String code = "TEST_CODE";

        PromptTemplateEntity latest = new PromptTemplateEntity();
        latest.setCode(code);
        latest.setVersion(1);
        latest.setStatus(PromptStatus.ACTIVE);
        latest.setName("Latest");

        try (var ignored = mockStatic(TenantContext.class)) {
            when(TenantContext.getCurrentTenant()).thenReturn(tenantId);
            when(repository.findFirstByCodeAndTenantIdOrderByVersionDesc(code, tenantId)).thenReturn(Optional.of(latest));
            when(repository.save(any(PromptTemplateEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toDomain(any(PromptTemplateEntity.class))).thenReturn(new PromptTemplate());

            service.createDraft(code);

            verify(repository).save(argThat(entity ->
                entity.getVersion() == 2 &&
                entity.getStatus() == PromptStatus.DRAFT &&
                entity.getCode().equals(code)
            ));
        }
    }

    @Test
    void when_activateVersion_then_archivePreviousActive() {
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        String code = "TEST_CODE";

        PromptTemplateEntity toActivate = new PromptTemplateEntity();
        toActivate.setId(templateId);
        toActivate.setCode(code);
        toActivate.setTenantId(tenantId);
        toActivate.setStatus(PromptStatus.DRAFT);

        PromptTemplateEntity currentlyActive = new PromptTemplateEntity();
        currentlyActive.setStatus(PromptStatus.ACTIVE);

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(templateId, tenantId)).thenReturn(Optional.of(toActivate));
            when(repository.findByCodeAndTenantIdAndStatus(code, tenantId, PromptStatus.ACTIVE)).thenReturn(Optional.of(currentlyActive));

            service.activateVersion(templateId);

            assertEquals(PromptStatus.ARCHIVED, currentlyActive.getStatus());
            assertEquals(PromptStatus.ACTIVE, toActivate.getStatus());
            verify(repository, times(2)).save(any());
        }
    }

    @Test
    void when_updateActiveVersion_then_throwException() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PromptTemplateEntity activeEntity = new PromptTemplateEntity();
        activeEntity.setId(id);
        activeEntity.setStatus(PromptStatus.ACTIVE);
        activeEntity.setCode("code1");
        activeEntity.setTemplate("Ola {{nome}}");
        activeEntity.setVersion(1);
        activeEntity.setTenantId(tenantId);

        PromptTemplate domain = new PromptTemplate();
        domain.setId(id);
        domain.setTemplate("Ola {{nome}}");

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(eq(id), eq(tenantId))).thenReturn(Optional.of(activeEntity));
            domain.setCode("code1");
            domain.setName("Updated");
            domain.setTemplate("Ola {{nome}} novo");
            PromptTemplateEntity saved = new PromptTemplateEntity();
            saved.setVersion(2);
            when(repository.save(any(PromptTemplateEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toDomain(any(PromptTemplateEntity.class))).thenAnswer(i -> {
                PromptTemplateEntity e = i.getArgument(0);
                PromptTemplate p = new PromptTemplate();
                p.setVersion(e.getVersion());
                return p;
            });
            PromptTemplate result = service.update(domain);
            assertNotNull(result);
            assertEquals(2, result.getVersion());
        }
    }

    // ===== Batch1 TDD additions =====

    @Test
    void when_create_with_validTemplate_then_extractsParamsAndSaves() {
        UUID tenantId = UUID.randomUUID();
        PromptTemplate input = new PromptTemplate();
        input.setCode("MY_CODE");
        input.setName("My Template");
        input.setTemplate("Olá {{nome}} {{tarefa}}");
        input.setParams(Set.of("fake_should_be_ignored"));
        input.setVersion(null);
        input.setStatus(null);

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.existsByTenantIdAndCode(tenantId, "MY_CODE")).thenReturn(false);
            when(repository.save(any(PromptTemplateEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toEntity(any(PromptTemplate.class))).thenAnswer(i -> {
                PromptTemplate p = i.getArgument(0);
                PromptTemplateEntity e = new PromptTemplateEntity();
                e.setCode(p.getCode());
                e.setName(p.getName());
                e.setTemplate(p.getTemplate());
                e.setParams(p.getParams());
                e.setVersion(p.getVersion());
                e.setStatus(p.getStatus());
                return e;
            });
            when(mapper.toDomain(any(PromptTemplateEntity.class))).thenAnswer(i -> {
                PromptTemplateEntity e = i.getArgument(0);
                PromptTemplate p = new PromptTemplate();
                p.setCode(e.getCode());
                p.setParams(e.getParams());
                p.setVersion(e.getVersion());
                p.setStatus(e.getStatus());
                p.setTemplate(e.getTemplate());
                return p;
            });

            PromptTemplate result = service.create(input);

            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo(PromptStatus.DRAFT);
            assertThat(result.getParams()).containsExactlyInAnyOrder("nome", "tarefa");
            verify(repository).save(argThat(entity ->
                entity.getParams() != null && entity.getParams().containsAll(Set.of("nome", "tarefa")) && entity.getParams().size() == 2
            ));
        }
    }

    @Test
    void when_create_with_invalidVariable_then_throwsInvalidTemplateVariableException() {
        UUID tenantId = UUID.randomUUID();
        String[] invalidTemplates = new String[]{
            "{{1bad}}",
            "{{}}",
            "{{var com espaço}}",
            "{{var}",
            "{{var} }",
            "{{b} }"
        };

        for (String invalid : invalidTemplates) {
            PromptTemplate input = new PromptTemplate();
            input.setCode("CODE_" + invalid.hashCode());
            input.setName("Test");
            input.setTemplate(invalid);
            try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
                mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
                when(repository.existsByTenantIdAndCode(eq(tenantId), any())).thenReturn(false);
                assertThatThrownBy(() -> service.create(input))
                    .isInstanceOf(InvalidTemplateVariableException.class);
            }
        }
    }

    @Test
    void when_create_withDuplicateCode_then_throwsPromptCodeDuplicateException() {
        UUID tenantId = UUID.randomUUID();
        PromptTemplate input = new PromptTemplate();
        input.setCode("DUPE");
        input.setName("Test");
        input.setTemplate("hello {{nome}}");

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.existsByTenantIdAndCode(tenantId, "DUPE")).thenReturn(true);

            assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(PromptCodeDuplicateException.class);
        }
    }

    @Test
    void when_update_withChangedCode_then_throwsCodeImmutableException() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PromptTemplateEntity existing = new PromptTemplateEntity();
        existing.setId(id);
        existing.setCode("ORIGINAL");
        existing.setVersion(2);
        existing.setTemplate("old {{x}}");
        existing.setTenantId(tenantId);

        PromptTemplate input = new PromptTemplate();
        input.setId(id);
        input.setCode("CHANGED");
        input.setName("Updated");
        input.setTemplate("new {{y}}");

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(input))
                .isInstanceOf(CodeImmutableException.class);
        }
    }

    @Test
    void when_update_withValidTemplate_then_incrementsVersion() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PromptTemplateEntity existing = new PromptTemplateEntity();
        existing.setId(id);
        existing.setCode("CODE1");
        existing.setVersion(2);
        existing.setTemplate("old {{nome}}");
        existing.setParams(Set.of("nome"));
        existing.setTenantId(tenantId);
        existing.setName("Old");
        existing.setStatus(PromptStatus.DRAFT);

        PromptTemplate input = new PromptTemplate();
        input.setId(id);
        input.setCode("CODE1");
        input.setName("New");
        input.setTemplate("Olá {{nome}} {{nova}}");

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));
            when(repository.save(any(PromptTemplateEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toDomain(any(PromptTemplateEntity.class))).thenAnswer(i -> {
                PromptTemplateEntity e = i.getArgument(0);
                PromptTemplate p = new PromptTemplate();
                p.setVersion(e.getVersion());
                p.setCode(e.getCode());
                p.setParams(e.getParams());
                return p;
            });

            PromptTemplate result = service.update(input);

            assertThat(result.getVersion()).isEqualTo(3);
            assertThat(result.getParams()).containsExactlyInAnyOrder("nome", "nova");
            verify(repository).save(argThat(entity -> entity.getVersion() == 3));
        }
    }

    @Test
    void when_delete_withAgentLinked_then_throwsPromptInUseException() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setCode("CODE");
        entity.setTenantId(tenantId);

        PromptTemplate input = new PromptTemplate();
        input.setId(id);

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(entity));
            when(agentRepository.existsByPromptTemplate_IdAndTenantId(id, tenantId)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(input))
                .isInstanceOf(PromptInUseException.class);
        }
    }

    @Test
    void when_deleteById_withAgentLinked_then_throwsPromptInUseException() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(id);
        entity.setCode("CODE");
        entity.setTenantId(tenantId);

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(entity));
            when(agentRepository.existsByPromptTemplate_IdAndTenantId(id, tenantId)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteById(id))
                .isInstanceOf(PromptInUseException.class);
        }
    }

    @Test
    void when_findById_withOtherTenant_then_throwsNotFound() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(PromptTemplateNotFoundException.class);
        }
    }

    @Test
    void when_findAll_withTenant_then_filtersTenant() {
        UUID tenantId = UUID.randomUUID();
        PromptTemplateEntity e1 = new PromptTemplateEntity();
        e1.setId(UUID.randomUUID());
        e1.setCode("C1");
        e1.setTenantId(tenantId);
        PromptTemplateEntity e2 = new PromptTemplateEntity();
        e2.setId(UUID.randomUUID());
        e2.setCode("C2");
        e2.setTenantId(tenantId);

        Page<PromptTemplateEntity> page = new PageImpl<>(List.of(e1, e2));

        try (MockedStatic<TenantContext> mocked = mockStatic(TenantContext.class)) {
            mocked.when(TenantContext::getCurrentTenant).thenReturn(tenantId);
            when(repository.findByTenantId(eq(tenantId), any(Pageable.class))).thenReturn(page);
            when(mapper.toDomain(any(List.class))).thenAnswer(i -> {
                List<PromptTemplateEntity> list = i.getArgument(0);
                return list.stream().map(ent -> {
                    PromptTemplate p = new PromptTemplate();
                    p.setId(ent.getId());
                    p.setCode(ent.getCode());
                    return p;
                }).toList();
            });

            List<PromptTemplate> result = service.findAll();

            assertThat(result).hasSize(2);
            verify(repository).findByTenantId(eq(tenantId), any(Pageable.class));
        }
    }
}
