package br.com.systec.hive.assistant.web.internal.v1.controller;

import br.com.systec.hive.assistant.api.domain.PromptStatus;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.service.PromptTemplateService;
import br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateDTO;
import br.com.systec.hive.assistant.web.internal.v1.mapper.PromptTemplateDTOMapper;
import br.com.systec.hive.commons.api.tools.exceptions.ControllerExceptionHandler;
import br.com.systec.hive.commons.impl.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PromptTemplateControllerV1Test {

    @Mock
    PromptTemplateService service;
    @Mock
    PromptTemplateDTOMapper mapper;

    @InjectMocks
    PromptTemplateControllerV1 controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ControllerExceptionHandler())
                .build();
    }

    private PromptTemplate dummyTemplate(UUID id) {
        PromptTemplate pt = new PromptTemplate();
        pt.setId(id);
        pt.setCode("greeting-v1");
        pt.setName("Saudação");
        pt.setTemplate("Olá {{nome}}");
        pt.setVersion(1);
        pt.setStatus(PromptStatus.DRAFT);
        pt.setCreateAt(LocalDateTime.now());
        return pt;
    }

    private PromptTemplateDTO dummyDTO(UUID id) {
        return new PromptTemplateDTO(id, "Saudação", "greeting-v1", "desc", List.of("geral"), "Olá {{nome}}",
                java.util.Set.of("nome"), null, PromptStatus.DRAFT, 1, java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    void when_create_with_valid_then_201() throws Exception {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        PromptTemplate domain = dummyTemplate(id);
        PromptTemplateDTO dto = dummyDTO(id);
        when(mapper.toDomain(any(br.com.systec.hive.assistant.web.internal.v1.dto.PromptTemplateCreateDTO.class))).thenReturn(domain);
        when(service.create(any(PromptTemplate.class))).thenReturn(domain);
        when(mapper.toDTO(any(PromptTemplate.class))).thenReturn(dto);
        String body = """
                {"name":"Saudação","code":"greeting-v1","template":"Olá {{nome}}"}
                """;
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(post("/v1/prompt-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isCreated());
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_create_with_invalidCode_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String body = """
                {"name":"Saudação","code":"bad code!","template":"Olá {{nome}}"}
                """;
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(post("/v1/prompt-templates")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_list_with_sizeOver50_then_400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                mockMvc.perform(get("/v1/prompt-templates?page=0&size=51"))
                        .andExpect(status().isBadRequest());
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }

    @Test
    void when_list_with_valid_then_200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        PromptTemplate domain = dummyTemplate(UUID.randomUUID());
        Page<PromptTemplate> page = new PageImpl<>(List.of(domain));
        when(service.findByTenantId(any(UUID.class), any(), any(), any())).thenReturn(page);
        when(mapper.toDTO(any(PromptTemplate.class))).thenReturn(dummyDTO(domain.getId()));
        TenantContext.runWithTenant(tenantId, () -> {
            try {
                var result = controller.list(0, 20, null, null);
                assertThat(result.getStatusCode().value()).isEqualTo(200);
                assertThat(result.getBody()).isNotNull();
            } catch (Exception e) { throw new RuntimeException(e); }
            return null;
        });
    }
}
