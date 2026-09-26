package br.com.systec.hive.agent.web.internal.v1.dto;

import br.com.systec.hive.provider.api.AiProvider;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    void when_createAgent_with_valid_then_noViolations() {
        AgentCreateDTO dto = new AgentCreateDTO(
                "Assistente", "https://cdn.example.com/avatar.png", "desc", "systemPrompt", null,
                "gpt-4o-mini", AiProvider.OPENAI, 0.7, 0.9, 2048, List.of("web_search"), List.of("kb1"), true, true);
        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void when_createAgent_with_invalidAvatar_then_violation() {
        AgentCreateDTO dto = new AgentCreateDTO(
                "Assistente", "not-a-url", "desc", "systemPrompt", null,
                "gpt-4o-mini", AiProvider.OPENAI, 0.7, 0.9, 2048, null, null, false, true);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createAgent_with_tooManyTools_then_violation() {
        List<String> tools = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "tool" + i).toList();
        AgentCreateDTO dto = new AgentCreateDTO(
                "Assistente", null, null, null, null,
                "gpt-4o-mini", AiProvider.OPENAI, null, null, null, tools, null, false, true);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createAgent_with_blankName_then_violation() {
        AgentCreateDTO dto = new AgentCreateDTO(
                "", null, null, null, null,
                "gpt-4o-mini", AiProvider.OPENAI, null, null, null, null, null, false, true);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createAgent_with_blankModel_then_violation() {
        AgentCreateDTO dto = new AgentCreateDTO(
                "Assistente", null, null, null, null,
                "  ", AiProvider.OPENAI, null, null, null, null, null, false, true);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_createAgent_with_nullProvider_then_violation() {
        AgentCreateDTO dto = new AgentCreateDTO(
                "Assistente", null, null, null, null,
                "gpt-4o-mini", null, null, null, null, null, null, false, true);
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    @Test
    void when_chatRequest_with_nullableAgentId_then_valid() {
        ChatRequestDTO dto = new ChatRequestDTO(null, null, "hello");
        assertThat(validator.validate(dto)).isEmpty();
        ChatRequestDTO dto2 = new ChatRequestDTO(java.util.UUID.randomUUID(), null, "hello");
        assertThat(validator.validate(dto2)).isEmpty();
    }

    @Test
    void when_chatRequest_with_blankMessage_then_violation() {
        ChatRequestDTO dto = new ChatRequestDTO(null, null, "");
        assertThat(validator.validate(dto)).isNotEmpty();
    }

    // ================= RN-KB4 — o limite de 5 precisa ser igual nos dois caminhos de escrita ======

    @Test
    void when_patchKnowledgeBases_with_sixIds_then_violation() {
        assertThat(validator.validate(new AgentKnowledgePatchDTO(sixKbIds())))
                .isNotEmpty();
    }

    @Test
    void when_patchKnowledgeBases_with_nullIds_then_violation() {
        assertThat(validator.validate(new AgentKnowledgePatchDTO(null))).isNotEmpty();
    }

    @Test
    void when_patchKnowledgeBases_with_fiveIds_then_noViolations() {
        // O limite é inclusivo: 5 tem que passar. Um @Size(max = 4) também reprovaria 5 e este
        // teste pegaria a regressão antes de alguém descobrir no formulário.
        assertThat(validator.validate(new AgentKnowledgePatchDTO(fiveKbIds()))).isEmpty();
        assertThat(validator.validate(new AgentKnowledgeBasesDTO(fiveKbIds()))).isEmpty();
    }

    @Test
    void when_patchAndPutKnowledgeBases_with_sixIds_then_rejectWithTheSameMessage() {
        // Os dois caminhos gravam a mesma relação, então a mesma ação do usuário não pode receber
        // "KB_TOO_MANY_LINKS" numa rota e "VALIDATION_ERROR" na outra.
        Set<String> patchMessages = messagesOf(new AgentKnowledgePatchDTO(sixKbIds()));
        Set<String> putMessages = messagesOf(new AgentKnowledgeBasesDTO(sixKbIds()));

        assertThat(patchMessages).isEqualTo(putMessages);
        assertThat(patchMessages).containsExactly("Máximo de 5 bases de conhecimento por agente");
    }

    @Test
    void when_putKnowledgeBases_with_sixIds_then_violation() {
        assertThat(validator.validate(new AgentKnowledgeBasesDTO(sixKbIds()))).isNotEmpty();
    }

    private Set<String> messagesOf(Object dto) {
        return validator.validate(dto).stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.toSet());
    }

    private static List<java.util.UUID> sixKbIds() {
        return java.util.stream.IntStream.range(0, 6).mapToObj(i -> java.util.UUID.randomUUID()).toList();
    }

    private static List<java.util.UUID> fiveKbIds() {
        return java.util.stream.IntStream.range(0, 5).mapToObj(i -> java.util.UUID.randomUUID()).toList();
    }
}
