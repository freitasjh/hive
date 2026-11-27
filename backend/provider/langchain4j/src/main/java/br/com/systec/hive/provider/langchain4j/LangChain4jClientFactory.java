package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.api.security.ProviderBaseUrlGuard;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class LangChain4jClientFactory implements LlmClientFactory<ChatModel> {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jClientFactory.class);

    static final String OLLAMA_DEFAULT_BASE_URL = "http://localhost:11434";

    /**
     * Read timeout for a generation. Measured on the same agent: 2.3 s (short), 9 s (medium) and
     * over 30 s (long) — a 1500-word request exceeded the old 30 s ceiling and the generation was
     * wasted. 120 s leaves room for long answers without letting a stuck provider hold the request
     * forever; the per-tenant rate limit is what bounds abuse, not this timeout.
     */
    static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(120);

    /**
     * The SDK retries every non-{@code NonRetriableException}, read timeouts included
     * (langchain4j 1.19.0 {@code RetryUtils.RetryPolicy}: "Remaining retries: 2 of 2" in the log).
     * 3 × 30 s ≈ 90 s of dead waiting before surfacing an error the user cannot act on, so the SDK
     * retry is off and {@link LlmRetryPolicy} takes over, retrying only 429/5xx.
     */
    static final int MAX_RETRIES = 0;

    static final String SESSION_HEADER = "x-opencode-session";
    static final String USER_AGENT_HEADER = "User-Agent";
    static final String HIVE_USER_AGENT = "hive/1.0";

    /**
     * Server-set headers for OpenCode GO/Zen, stable per chat session.
     * The factory owns this map — callers have no input path, so clients cannot override it.
     * User-Agent here last-write-wins over the langchain4j default.
     * Only GO/ZEN clients receive these headers.
     */
    static Map<String, String> opencodeHeaders(UUID sessionId) {
        // sessionId must not be null: sessionId.toString() is used verbatim as the
        // x-opencode-session header value. A null would either NPE here or, if coerced,
        // be sent as the literal "null". Callers in the stateless public flow pass a
        // synthetic per-request UUID (see createClientWithSession contract).
        return Map.of(
                SESSION_HEADER, sessionId.toString(),
                USER_AGENT_HEADER, HIVE_USER_AGENT);
    }

    static Map<String, String> opencodeHeaders() {
        return opencodeHeaders(UUID.randomUUID());
    }

    @Override
    public ChatModel createClient(AiProvider provider, String model, Double temperature, Integer maxTokens,
                                  Double topP, String apiKey, String baseUrl) {
        return createClientWithSession(provider, model, temperature, maxTokens, topP, apiKey, baseUrl,
                UUID.randomUUID());
    }

    @Override
    public ChatModel createClientWithSession(AiProvider provider, String model, Double temperature, Integer maxTokens,
            Double topP, String apiKey, String baseUrl, UUID sessionId) {
        // Contract (Gate R2 of the public-api-chat batch): sessionId is NOT nullable here.
        //   - opencodeHeaders(UUID) calls sessionId.toString() for the x-opencode-session header,
        //     so a null would surface as the literal string "null" (or an NPE) in the header.
        //   - The factory rejects null with IllegalArgumentException rather than silently degrading.
        //   Therefore, in the stateless public flow, the caller (LangChain4jTurnGateway via
        //   TurnRequest) must supply a synthetic per-request UUID (the turn id of the response);
        //   ChatTrace.sessionId stays null on that path. Do not relax this check to tolerate null.
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
            case OPENAI -> openAi(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey), null);
            case OPENAI_COMPATIBLE -> openAi(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey),
                    requireBaseUrl(baseUrl));
            case OPENCODE_GO -> opencode(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey),
                    VendorCatalog.baseUrlFor(AiProvider.OPENCODE_GO), sessionId);
            case OPENCODE_ZEN -> opencode(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey),
                    VendorCatalog.baseUrlFor(AiProvider.OPENCODE_ZEN), sessionId);
            case OLLAMA -> ollama(model.trim(), temp, maxTokens, topP, resolveOllamaBaseUrl(baseUrl));
            case GEMINI -> gemini(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey));
            case ANTHROPIC -> anthropic(model.trim(), temp, maxTokens, topP, requireApiKey(provider, apiKey));
        };
    }

    private ChatModel openAi(String model, double temperature, Integer maxTokens, Double topP,
                             String apiKey, String baseUrl) {
        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES);
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    private ChatModel opencode(String model, double temperature, Integer maxTokens, Double topP, String apiKey,
                               String baseUrl, UUID sessionId) {
        var builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .baseUrl(baseUrl)
                .customHeaders(() -> opencodeHeaders(sessionId));
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    private ChatModel ollama(String model, double temperature, Integer maxTokens, Double topP, String baseUrl) {
        var builder = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES);
        if (maxTokens != null) {
            builder.numPredict(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    private ChatModel gemini(String model, double temperature, Integer maxTokens, Double topP, String apiKey) {
        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES);
        if (maxTokens != null) {
            builder.maxOutputTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    private ChatModel anthropic(String model, double temperature, Integer maxTokens, Double topP, String apiKey) {
        var builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .timeout(CLIENT_TIMEOUT)
                .maxRetries(MAX_RETRIES);
        // null => langchain4j default (1024). Never 0: Anthropic rejects a zero max_tokens.
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (topP != null) {
            builder.topP(topP);
        }
        return builder.build();
    }

    static String requireApiKey(AiProvider provider, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("API key ausente para provider {}.", provider);
            throw new IllegalArgumentException("apiKey é obrigatória para o provider " + provider + ".");
        }
        return apiKey.trim();
    }

    static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl é obrigatória para OPENAI_COMPATIBLE.");
        }
        String normalized = baseUrl.trim();
        ProviderBaseUrlGuard.validate(normalized);
        return normalized;
    }

    static String resolveOllamaBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return OLLAMA_DEFAULT_BASE_URL;
        }
        String normalized = baseUrl.trim();
        ProviderBaseUrlGuard.validate(normalized);
        return normalized;
    }
}
