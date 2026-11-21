package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-021/RF-02 — conversao aditiva de {@link PromptTemplate} para {@link TurnPrompt}.
 *
 * <p>{@code InferenceParams} e (temperature, topP, maxTokens); {@code TurnPrompt} e
 * (templateId, text, temperature, maxTokens, topP). A ordem trocada dos dois ultimos e o motivo
 * desta classe existir: a fabrica nomeada le por nome, nunca por posicao.
 */
class TurnPromptsTest {

    @Test
    void when_templateHasInferenceParams_then_factoryCopiesEachFieldByName() {
        UUID id = UUID.randomUUID();
        PromptTemplate template = new PromptTemplate();
        template.setId(id);
        template.setTemplate("texto escolhido");
        // (temperature, topP, maxTokens) — ordem de InferenceParams.
        template.setInferenceParams(new InferenceParams(0.7, 0.9, 256));

        TurnPrompt prompt = TurnPrompts.from(template);

        assertThat(prompt.templateId()).isEqualTo(id);
        assertThat(prompt.text()).isEqualTo("texto escolhido");
        assertThat(prompt.temperature()).isEqualTo(0.7);
        assertThat(prompt.maxTokens()).isEqualTo(256);
        assertThat(prompt.topP()).isEqualTo(0.9);
    }

    @Test
    void when_templateHasNoInferenceParams_then_limitsAreNull() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("sem limites");

        TurnPrompt prompt = TurnPrompts.from(template);

        assertThat(prompt.temperature()).isNull();
        assertThat(prompt.maxTokens()).isNull();
        assertThat(prompt.topP()).isNull();
    }

    @Test
    void when_templateTextIsBlank_then_factoryKeepsItBlankForTheCallerFallback() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("   ");

        assertThat(TurnPrompts.from(template).text()).isEqualTo("   ");
    }
}
