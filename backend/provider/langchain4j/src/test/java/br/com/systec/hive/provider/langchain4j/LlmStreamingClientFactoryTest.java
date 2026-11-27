package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.model.anthropic.AnthropicChatRequestParameters;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PS-01 — the streaming factory covers all seven providers with the knobs frozen by ADR-001 D1.
 * Written before {@link LangChain4jStreamingClientFactory} existed.
 */
class LlmStreamingClientFactoryTest {

    private final LlmStreamingClientFactory<StreamingChatModel> factory =
            new LangChain4jStreamingClientFactory();

    private StreamingChatModel create(AiProvider provider) {
        return factory.createStreamingClient(provider, "model-x", 0.3, 512, 0.9,
                "test-key", "http://localhost:1234/v1");
    }

    @Test
    void when_createStreamingClient_forEveryProvider_then_returnsTypedStreamingClient() {
        assertThat(create(AiProvider.OPENAI)).isInstanceOf(OpenAiStreamingChatModel.class);
        assertThat(create(AiProvider.OPENAI_COMPATIBLE)).isInstanceOf(OpenAiStreamingChatModel.class);
        assertThat(create(AiProvider.OPENCODE_GO)).isInstanceOf(OpenAiStreamingChatModel.class);
        assertThat(create(AiProvider.OPENCODE_ZEN)).isInstanceOf(OpenAiStreamingChatModel.class);
        assertThat(create(AiProvider.OLLAMA)).isInstanceOf(OllamaStreamingChatModel.class);
        assertThat(create(AiProvider.GEMINI)).isInstanceOf(GoogleAiGeminiStreamingChatModel.class);
        assertThat(create(AiProvider.ANTHROPIC)).isInstanceOf(AnthropicStreamingChatModel.class);
    }

    @Test
    void when_createStreamingClient_openAi_then_thinkingKnobsApplied() throws Exception {
        OpenAiStreamingChatModel model = (OpenAiStreamingChatModel) create(AiProvider.OPENAI);

        assertThat(field(model, "returnThinking")).isEqualTo(true);
        assertThat(field(model, "sendThinking")).isEqualTo(true);
        assertThat(field(model, "thinkingFieldName")).isEqualTo("reasoning_content");
    }

    @Test
    void when_createStreamingClient_anthropic_then_thinkingTypeAndBudgetApplied() {
        AnthropicStreamingChatModel model = (AnthropicStreamingChatModel) create(AiProvider.ANTHROPIC);
        AnthropicChatRequestParameters params = (AnthropicChatRequestParameters) model.defaultRequestParameters();

        assertThat(params.thinkingType()).isEqualTo("enabled");
        assertThat(params.thinkingBudgetTokens()).isEqualTo(2_048);
    }

    @Test
    void when_createStreamingClient_ollama_then_thinkEnabled() {
        OllamaStreamingChatModel model = (OllamaStreamingChatModel) create(AiProvider.OLLAMA);
        OllamaChatRequestParameters params = (OllamaChatRequestParameters) model.defaultRequestParameters();

        assertThat(params.think()).isTrue();
    }

    @Test
    void when_createStreamingClient_gemini_then_onlyReturnThinkingApplied() throws Exception {
        GoogleAiGeminiStreamingChatModel model = (GoogleAiGeminiStreamingChatModel) create(AiProvider.GEMINI);

        assertThat(field(model, "returnThinking")).isEqualTo(true);
        assertThat(field(model, "thinkingConfig")).as("ADR-001 D1: no invented thinkingConfig").isNull();
    }

    @Test
    void when_createStreamingClient_withoutApiKey_then_throwsInsteadOfSilentDegradation() {
        for (AiProvider provider : java.util.List.of(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.ANTHROPIC)) {
            assertThatThrownBy(() -> factory.createStreamingClient(provider, "model-x", null, null, null,
                    null, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void when_createStreamingClient_withNullProvider_then_throws() {
        assertThatThrownBy(() -> create(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_createStreamingClient_then_defaultInterfaceIsNotBroken() {
        assertThat(LlmStreamingClientFactory.class.isAssignableFrom(LangChain4jStreamingClientFactory.class)).isTrue();
    }

    private static Object field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
