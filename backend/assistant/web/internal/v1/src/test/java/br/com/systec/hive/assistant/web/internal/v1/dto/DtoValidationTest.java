package br.com.systec.hive.assistant.web.internal.v1.dto;

import java.util.List;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void when_createPrompt_with_valid_then_noViolations() {
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO(
                "Saudação", "greeting-v1", "desc", List.of("geral"), "Olá {{nome}}", new InferenceParamsDTO(0.7, 0.9, 2048), null);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void when_createPrompt_with_invalidCode_then_violation() {
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO(
                "Saudação", "bad code!", "desc", List.of("geral"), "Olá {{nome}}", null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createPrompt_with_blankName_then_violation() {
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO(
                "", "greeting-v1", null, null, "Olá {{nome}}", null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createPrompt_with_tooLongTemplate_then_violation() {
        String longTemplate = "a".repeat(20001);
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO(
                "Saudação", "greeting-v1", null, null, longTemplate, null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createPrompt_with_tooManyTags_then_violation() {
        List<String> tags = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tag" + i).toList();
        PromptTemplateCreateDTO dto = new PromptTemplateCreateDTO(
                "Saudação", "greeting-v1", null, tags, "Olá {{nome}}", null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_inferenceParams_with_outOfRange_then_violation() {
        InferenceParamsDTO dto = new InferenceParamsDTO(3.0, 0.9, 2048);
        assertThat(validator.validate(dto)).isNotEmpty();
        InferenceParamsDTO dto2 = new InferenceParamsDTO(0.5, 1.5, 2048);
        assertThat(validator.validate(dto2)).isNotEmpty();
        InferenceParamsDTO dto3 = new InferenceParamsDTO(0.5, 0.9, 100);
        assertThat(validator.validate(dto3)).isNotEmpty();
    }

    @Test
    void when_createSession_with_valid_then_noViolations() {
        CreateSessionDTO dto = new CreateSessionDTO(java.util.UUID.randomUUID(), "Titulo", null, null);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void when_createSession_with_nullPromptTemplate_then_violation() {
        // Não há prompt padrão e não há conversa sem prompt: a coluna é NOT NULL e o serviço exige
        // o template. Um DTO que aceitasse null aqui descreveria uma sessão que o banco rejeita.
        CreateSessionDTO dto = new CreateSessionDTO(null, "Titulo", null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createSession_with_titleTooLong_then_violation() {
        CreateSessionDTO dto = new CreateSessionDTO(java.util.UUID.randomUUID(), "t".repeat(51), null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_chatRequest_with_nullablePromptTemplateId_then_valid() {
        // Válido no DTO e recusado no serviço: um turno em sessão existente não manda template, e a
        // distinção entre "nova conversa" e "continuar" só o serviço consegue fazer.
        ChatRequestDTO dto = new ChatRequestDTO(null, null, "hello", null, null);
        assertThat(validator.validate(dto)).isEmpty();
        ChatRequestDTO dto2 = new ChatRequestDTO(java.util.UUID.randomUUID(), null, "hello", null, null);
        assertThat(validator.validate(dto2)).isEmpty();
    }

    @Test
    void when_chatRequest_with_blankMessage_then_violation() {
        ChatRequestDTO dto = new ChatRequestDTO(null, null, "", null, null);
        assertThat(validator.validate(dto)).isNotEmpty();
    }
}
