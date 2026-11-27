package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import br.com.systec.hive.provider.api.VendorCatalog;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Streaming client factory for the seven {@link AiProvider} values (ADR-001 D1). */
@Component
public class LangChain4jStreamingClientFactory implements LlmStreamingClientFactory<StreamingChatModel> {

    /** Thinking field name for the OpenAI-compatible wire; verified on the 1.19.0 builder (S7). */
    private static final String THINKING_FIELD = "reasoning_content";
    private static final String ANTHROPIC_THINKING_TYPE = "enabled";
    private static final int ANTHROPIC_THINKING_BUDGET = 2_048;

    // Inherited synchronous slot: on a streaming-only factory it must still return T.
    @Override
    public StreamingChatModel createClient(AiProvider provider, String model, Double temperature, Integer maxTokens,
            Double topP, String apiKey, String baseUrl) {
        return createStreamingClient(provider, model, temperature, maxTokens, topP, apiKey, baseUrl);
    }

    @Override
    public StreamingChatModel createStreamingClient(AiProvider provider, String model, Double temperature,
            Integer maxTokens, Double topP, String apiKey, String baseUrl) {
        return createStreamingClientWithSession(provider, model, temperature, maxTokens, topP, apiKey, baseUrl,
                UUID.randomUUID());
    }

    @Override
    public StreamingChatModel createStreamingClientWithSession(AiProvider provider, String model,
            Double temperature, Integer maxTokens, Double topP, String apiKey, String baseUrl, UUID sessionId) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null");
        }
        double temp = temperature != null ? temperature : 0.7;
        return switch (provider) {
            case OPENAI -> openAi(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey), null, null);
            case OPENAI_COMPATIBLE -> openAi(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey),
                    LangChain4jClientFactory.requireBaseUrl(baseUrl), null);
            case OPENCODE_GO -> openAi(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey),
                    VendorCatalog.baseUrlFor(AiProvider.OPENCODE_GO),
                    LangChain4jClientFactory.opencodeHeaders(sessionId));
            case OPENCODE_ZEN -> openAi(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey),
                    VendorCatalog.baseUrlFor(AiProvider.OPENCODE_ZEN),
                    LangChain4jClientFactory.opencodeHeaders(sessionId));
            case OLLAMA -> ollama(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.resolveOllamaBaseUrl(baseUrl));
            case GEMINI -> gemini(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey));
            case ANTHROPIC -> anthropic(model.trim(), temp, maxTokens, topP,
                    LangChain4jClientFactory.requireApiKey(provider, apiKey));
        };
    }

    private static StreamingChatModel openAi(String model, double temperature, Integer maxTokens, Double topP,
            String apiKey, String baseUrl, java.util.Map<String, String> headers) {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(LangChain4jClientFactory.CLIENT_TIMEOUT)
                .returnThinking(true)
                .sendThinking(true, THINKING_FIELD);
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        if (headers != null) {
            builder.customHeaders(headers);
        }
        return builder.build();
    }

    private static StreamingChatModel ollama(String model, double temperature, Integer maxTokens, Double topP,
            String baseUrl) {
        var builder = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .timeout(LangChain4jClientFactory.CLIENT_TIMEOUT)
                .think(true);
        if (maxTokens != null) {
            builder.numPredict(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    private static StreamingChatModel gemini(String model, double temperature, Integer maxTokens, Double topP,
            String apiKey) {
        var builder = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(LangChain4jClientFactory.CLIENT_TIMEOUT)
                .returnThinking(true);
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    private static StreamingChatModel anthropic(String model, double temperature, Integer maxTokens, Double topP,
            String apiKey) {
        var builder = AnthropicStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(LangChain4jClientFactory.CLIENT_TIMEOUT)
                .thinkingType(ANTHROPIC_THINKING_TYPE)
                .thinkingBudgetTokens(ANTHROPIC_THINKING_BUDGET);
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }
}
