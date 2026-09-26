package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.VendorCatalog;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jClientFactoryTest {

    private final LangChain4jClientFactory factory = new LangChain4jClientFactory();

    @Test
    void when_create_with_openaiAndKey_then_buildsClient() {
        ChatModel client = factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, null, null, "sk-fake-test-key", null);

        assertThat(client).isNotNull();
    }

    @Test
    void when_create_with_ollamaWithoutKey_then_buildsClientWithDefaultBaseUrl() {
        ChatModel client = factory.createClient(AiProvider.OLLAMA, "llama3.2", 0.7, null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void when_create_with_geminiAndKey_then_buildsClient() {
        ChatModel client = factory.createClient(AiProvider.GEMINI, "gemini-2.0-flash", 0.7, null, null, "AIzaFakeTestKey", null);

        assertThat(client).isNotNull();
    }

    @Test
    void when_create_with_anthropicAndKey_then_buildsClient() {
        ChatModel client = factory.createClient(AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", 0.7, null, null, "sk-ant-fake-test-key", null);

        assertThat(client).isNotNull();
    }

    @Test
    void when_create_with_compatibleAndBaseUrl_then_buildsClient() {
        ChatModel client = factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, null, null,
                "custom-fake-key", "https://203.0.113.10/v1");

        assertThat(client).isNotNull();
    }

    @Test
    void when_create_with_nullProvider_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(null, "gpt-4o-mini", 0.7, null, null, "sk-fake", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_blankModel_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENAI, "  ", 0.7, null, null, "sk-fake", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_openaiWithoutKey_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_compatibleWithoutBaseUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, null, null, "key", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_privateBaseUrl_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, null, null,
                        "key", "http://169.254.169.254/"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> factory.createClient(AiProvider.OLLAMA, "llama3.2", 0.7, null, null,
                        null, "http://192.168.1.10:11434"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_goOrZen_then_buildsClient() {
        ChatModel go = factory.createClient(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, "go-fake-key", null);
        ChatModel zen = factory.createClient(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5", 0.7, null, null, "zen-fake-key", null);

        assertThat(go).isNotNull();
        assertThat(zen).isNotNull();
    }

    @Test
    void when_create_with_goWithoutKey_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.createClient(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5", 0.7, null, null, "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- A1: maxTokens / topP

    @Test
    void when_create_with_limits_then_appliesMaxTokensAndTopP_onOpenAi() throws Exception {
        ChatModel client = factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, 512, 0.9, "sk-fake-test-key", null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(512);
        assertThat(parametersOf(client).topP()).isEqualTo(0.9);
    }

    @Test
    void when_create_withLimits_then_appliesMaxTokensAndTopP_onOpenAiCompatible() throws Exception {
        ChatModel client = factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, 256, 0.5,
                "custom-fake-key", "https://203.0.113.10/v1");

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(256);
        assertThat(parametersOf(client).topP()).isEqualTo(0.5);
    }

    @Test
    void when_create_withLimits_then_appliesMaxTokensAndTopP_onOpencode() throws Exception {
        UUID session = UUID.randomUUID();
        ChatModel go = factory.createClientWithSession(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, 1024, 0.8,
                "go-fake-key", null, session);
        ChatModel zen = factory.createClientWithSession(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5", 0.7, 2048, 0.4,
                "zen-fake-key", null, session);

        assertThat(parametersOf(go).maxOutputTokens()).isEqualTo(1024);
        assertThat(parametersOf(go).topP()).isEqualTo(0.8);
        assertThat(parametersOf(zen).maxOutputTokens()).isEqualTo(2048);
        assertThat(parametersOf(zen).topP()).isEqualTo(0.4);
    }

    /** Ollama calls the generation ceiling {@code numPredict}, which maps to maxOutputTokens. */
    @Test
    void when_create_withLimits_then_appliesMaxTokensAndTopP_onOllama() throws Exception {
        ChatModel client = factory.createClient(AiProvider.OLLAMA, "llama3.2", 0.7, 333, 0.3, null, null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(333);
        assertThat(parametersOf(client).topP()).isEqualTo(0.3);
    }

    @Test
    void when_create_withLimits_then_appliesMaxTokensAndTopP_onGemini() throws Exception {
        ChatModel client = factory.createClient(AiProvider.GEMINI, "gemini-2.0-flash", 0.7, 777, 0.2, "AIzaFakeTestKey", null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(777);
        assertThat(parametersOf(client).topP()).isEqualTo(0.2);
    }

    @Test
    void when_create_withLimits_then_appliesMaxTokensAndTopP_onAnthropic() throws Exception {
        ChatModel client = factory.createClient(AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", 0.7, 999, 0.6,
                "sk-ant-fake-test-key", null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(999);
        assertThat(parametersOf(client).topP()).isEqualTo(0.6);
    }

    /**
     * {@code null} must stay {@code null} so the provider default applies. Coercing to 0 makes
     * Anthropic reject the request and makes OpenAI generate nothing.
     */
    @Test
    void when_createWithNullLimits_then_providerDefaultsAreKept() throws Exception {
        for (AiProvider provider : java.util.List.of(AiProvider.OPENAI, AiProvider.OPENAI_COMPATIBLE,
                AiProvider.OPENCODE_GO, AiProvider.OPENCODE_ZEN, AiProvider.OLLAMA, AiProvider.GEMINI)) {
            ChatRequestParameters parameters = parametersOf(factory.createClient(provider, modelFor(provider), 0.7,
                    null, null, "k", baseUrlFor(provider)));

            assertThat(parameters.maxOutputTokens())
                    .as("maxOutputTokens on %s must stay unset, never 0", provider)
                    .isNull();
            assertThat(parameters.topP()).as("topP on %s must stay unset", provider).isNull();
        }
    }

    /** Anthropic's langchain4j default is 1024; this fix must not change it when no ceiling is set. */
    @Test
    void when_createWithNullMaxTokens_then_anthropicKeepsItsOwnDefault() throws Exception {
        ChatModel client = factory.createClient(AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", 0.7, null, null,
                "sk-ant-fake-test-key", null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(1024);
        assertThat(parametersOf(client).topP()).isNull();
    }

    @Test
    void when_create_withZeroMaxTokens_then_zeroIsHonouredAsExplicitRequest() throws Exception {
        // Guards the null-vs-zero distinction: an explicit 0 is the caller's choice, not a bug.
        ChatModel client = factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, 0, null, "sk-fake-test-key", null);

        assertThat(parametersOf(client).maxOutputTokens()).isEqualTo(0);
    }

    // ---------------------------------------------------------------- A2: timeout

    @Test
    void when_createClientTimeout_then_is120Seconds() {
        // 2.3s short / 9s medium / >30s long measured on the same agent: 30s cut off long answers.
        assertThat(LangChain4jClientFactory.CLIENT_TIMEOUT).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void when_create_then_effectiveReadTimeoutIs120Seconds_onEveryProvider() throws Exception {
        assertThat(readTimeoutOf(factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, null, null, "k", null)))
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(readTimeoutOf(factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, null, null,
                "k", "https://203.0.113.10/v1"))).isEqualTo(Duration.ofSeconds(120));
        assertThat(readTimeoutOf(factory.createClient(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, "k", null)))
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(readTimeoutOf(factory.createClient(AiProvider.OLLAMA, "llama3.2", 0.7, null, null, null, null)))
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(readTimeoutOf(factory.createClient(AiProvider.GEMINI, "gemini-2.0-flash", 0.7, null, null, "k", null)))
                .isEqualTo(Duration.ofSeconds(120));
        assertThat(readTimeoutOf(factory.createClient(AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", 0.7, null, null, "k", null)))
                .isEqualTo(Duration.ofSeconds(120));
    }

    // ---------------------------------------------------------------- A3: retry off in the SDK

    @Test
    void when_createClientRetries_then_sdkRetryIsDisabledOnEveryProvider() throws Exception {
        // 3 x 30s of dead waiting on a read timeout is what produced the ~90s stall.
        assertThat(LangChain4jClientFactory.MAX_RETRIES).isZero();
        for (ChatModel client : allProviders(64, 0.5)) {
            assertThat(sdkRetriesOf(client)).as("sdk retries on %s", client.getClass().getSimpleName()).isZero();
        }
    }

    // ---------------------------------------------------------------- session headers (pre-existing)

    @Test
    void when_opencodeHeaders_with_sessionId_then_stablePerSession() {
        UUID session = UUID.randomUUID();
        Map<String, String> first = LangChain4jClientFactory.opencodeHeaders(session);
        Map<String, String> second = LangChain4jClientFactory.opencodeHeaders(session);

        assertThat(first).containsKey("x-opencode-session");
        assertThat(first.get("User-Agent")).isEqualTo("hive/1.0");
        assertThat(UUID.fromString(first.get("x-opencode-session"))).isNotNull();
        assertThat(first.get("x-opencode-session")).isEqualTo(session.toString());
        assertThat(second.get("x-opencode-session")).isEqualTo(first.get("x-opencode-session"));
    }

    @Test
    void when_opencodeHeaders_with_differentSessions_then_differentValues() {
        Map<String, String> first = LangChain4jClientFactory.opencodeHeaders(UUID.randomUUID());
        Map<String, String> second = LangChain4jClientFactory.opencodeHeaders(UUID.randomUUID());

        assertThat(second.get("x-opencode-session")).isNotEqualTo(first.get("x-opencode-session"));
    }

    @Test
    void when_createClientWithSession_with_goOrZen_then_headersStablePerSession() throws Exception {
        UUID session = UUID.randomUUID();
        ChatModel go = factory.createClientWithSession(
                AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, "go-fake-key", null, session);

        assertThat(pinnedBaseUrl(go)).isEqualTo(VendorCatalog.OPENCODE_GO_BASE_URL);
        assertThat(pinnedBaseUrl(go)).endsWith("/v1");
        Map<String, String> first = pinnedHeaders(go);
        Map<String, String> second = pinnedHeaders(go);
        assertThat(first.get("x-opencode-session")).isEqualTo(session.toString());
        assertThat(second.get("x-opencode-session")).isEqualTo(session.toString());
    }

    @Test
    void when_createClientWithSession_with_nullSession_then_throwsIllegalArgument() {
        assertThatThrownBy(() -> factory.createClientWithSession(
                        AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, "go-fake-key", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_create_with_goOrZen_then_fixedBaseUrlAndSessionHeaders() throws Exception {
        ChatModel go = factory.createClient(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, null, null, "go-fake-key", "https://evil.example/v1");

        assertThat(pinnedBaseUrl(go)).isEqualTo(VendorCatalog.OPENCODE_GO_BASE_URL);
        assertThat(pinnedBaseUrl(go)).endsWith("/v1");
        assertThat(pinnedHeaders(go)).containsKey("x-opencode-session");
        assertThat(pinnedHeaders(go).get("User-Agent")).isEqualTo("hive/1.0");

        ChatModel zen = factory.createClient(AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5", 0.7, null, null, "zen-fake-key", null);

        assertThat(pinnedBaseUrl(zen)).isEqualTo(VendorCatalog.OPENCODE_ZEN_BASE_URL);
        assertThat(pinnedBaseUrl(zen)).endsWith("/v1");
    }

    @Test
    void when_create_with_openai_then_noOpencodeHeaders() throws Exception {
        ChatModel openai = factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, null, null, "sk-fake-test-key", null);

        assertThat(pinnedHeaders(openai)).doesNotContainKey("x-opencode-session");
    }

    // ---------------------------------------------------------------- helpers

    private java.util.List<ChatModel> allProviders(Integer maxTokens, Double topP) {
        return java.util.List.of(
                factory.createClient(AiProvider.OPENAI, "gpt-4o-mini", 0.7, maxTokens, topP, "k", null),
                factory.createClient(AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", 0.7, maxTokens, topP, "k", "https://203.0.113.10/v1"),
                factory.createClient(AiProvider.OPENCODE_GO, "mimo-v2.5", 0.7, maxTokens, topP, "k", null),
                factory.createClient(AiProvider.OLLAMA, "llama3.2", 0.7, maxTokens, topP, null, null),
                factory.createClient(AiProvider.GEMINI, "gemini-2.0-flash", 0.7, maxTokens, topP, "k", null),
                factory.createClient(AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", 0.7, maxTokens, topP, "k", null));
    }

    private static String modelFor(AiProvider provider) {
        return provider == AiProvider.OLLAMA ? "llama3.2" : "gpt-4o-mini";
    }

    private static String baseUrlFor(AiProvider provider) {
        return provider == AiProvider.OPENAI_COMPATIBLE ? "https://203.0.113.10/v1" : null;
    }

    private static ChatRequestParameters parametersOf(ChatModel client) throws Exception {
        Object parameters = readFieldDeep(client, "defaultRequestParameters");
        assertThat(parameters).as("defaultRequestParameters on %s", client.getClass().getSimpleName())
                .isInstanceOf(ChatRequestParameters.class);
        return (ChatRequestParameters) parameters;
    }

    /** OpenAI/Ollama/Anthropic name it {@code maxRetries}; Gemini names it {@code maximumRetries}. */
    private static int sdkRetriesOf(ChatModel client) throws Exception {
        Object value = readFieldDeep(client, "maxRetries");
        if (value == null) {
            value = readFieldDeep(client, "maximumRetries");
        }
        assertThat(value).as("sdk retry field on %s", client.getClass().getSimpleName()).isNotNull();
        return ((Number) value).intValue();
    }

    /**
     * The vendors keep the transport in differently named fields ({@code client} for
     * OpenAI/Ollama/Anthropic, {@code geminiService} for Gemini) and store the read timeout either
     * directly or on a nested {@code HttpClient}, so both shapes are searched.
     */
    private static Duration readTimeoutOf(ChatModel client) throws Exception {
        for (String root : new String[]{"client", "geminiService", "defaultRequestParameters"}) {
            Duration found = findReadTimeout(readFieldDeep(client, root), 0);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("no effective read timeout found on " + client.getClass().getName());
    }

    private static Duration findReadTimeout(Object target, int depth) throws Exception {
        if (target == null || depth > 3) {
            return null;
        }
        Object value = readFieldDeep(target, "readTimeout");
        if (value instanceof Duration duration) {
            return duration;
        }
        for (String nested : new String[]{"httpClient", "delegate", "client", "geminiService"}) {
            Duration found = findReadTimeout(readFieldDeep(target, nested), depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String pinnedBaseUrl(ChatModel client) throws Exception {
        Object inner = readFieldDeep(client, "client");
        return (String) readFieldDeep(inner, "baseUrl");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> pinnedHeaders(ChatModel client) throws Exception {
        Object inner = readFieldDeep(client, "client");
        Object supplier = readFieldDeep(inner, "customHeadersSupplier");
        return ((java.util.function.Supplier<Map<String, String>>) supplier).get();
    }

    /** Walks the class hierarchy: several vendors declare these fields on a superclass. */
    private static Object readFieldDeep(Object target, String name) throws Exception {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
    }
}
