package br.com.systec.hive.provider.langchain4j;

import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SHD-15 / F4 — the streaming client carries the same session header as the synchronous one. */
class StreamingSessionHeaderTest {

    private final LlmStreamingClientFactory<StreamingChatModel> factory = new LangChain4jStreamingClientFactory();

    @Test
    void when_createWithSession_then_opencodeHeaderMatchesSynchronousClient() throws Exception {
        UUID sessionId = UUID.randomUUID();
        StreamingChatModel streaming = factory.createStreamingClientWithSession(AiProvider.OPENCODE_GO,
                "model-x", 0.3, 128, 0.9, "key", null, sessionId);
        var sync = new LangChain4jClientFactory().createClientWithSession(AiProvider.OPENCODE_GO,
                "model-x", 0.3, 128, 0.9, "key", null, sessionId);

        Map<String, String> streamingHeaders = headersOf(streaming);
        Map<String, String> syncHeaders = headersOf(sync);
        assertThat(streamingHeaders).isEqualTo(syncHeaders);
        assertThat(streamingHeaders).containsEntry("x-opencode-session", sessionId.toString())
                .containsEntry("User-Agent", "hive/1.0");
    }

    @Test
    void when_sessionIdNull_then_throwsBeforeClientCreation() {
        assertThatThrownBy(() -> factory.createStreamingClientWithSession(AiProvider.OPENCODE_GO,
                "model-x", null, null, null, "key", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_factoryHasNoSessionConcept_then_defaultDelegatesToCreateStreamingClient() {
        AtomicInteger calls = new AtomicInteger();
        LlmStreamingClientFactory<StreamingChatModel> plain = new LlmStreamingClientFactory<>() {
            @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return null; }
            @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { calls.incrementAndGet(); return null; }
        };

        plain.createStreamingClientWithSession(AiProvider.OLLAMA, "m", null, null, null, null, null,
                UUID.randomUUID());

        assertThat(calls).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headersOf(Object model) throws Exception {
        Object client = field(model, "client");
        Supplier<Map<String, String>> supplier = (Supplier<Map<String, String>>) field(client, "customHeadersSupplier");
        return supplier.get();
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
