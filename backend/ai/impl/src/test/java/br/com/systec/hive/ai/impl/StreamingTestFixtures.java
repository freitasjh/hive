package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.port.TurnPromptPolicy;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Shared fake provider for the streaming guard tests. Test-only. */
final class StreamingTestFixtures {

    private StreamingTestFixtures() {
    }

    static TurnRequest request(UUID sessionId) {
        return new TurnRequest(sessionId, new TurnPrompt(UUID.randomUUID(), "system", 0.3, 128, 0.9),
                AiProvider.OPENAI, "model-x", "key", null, List.of(UserMessage.from("question")));
    }

    /** Test policy: a blank template falls back to a label, not to the assistant's real constant. */
    static TurnPromptPolicy policy() {
        return text -> text == null || text.isBlank() ? "test-default" : text;
    }

    static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    static LlmStreamingClientFactory<StreamingChatModel> factory(
            AtomicReference<StreamingChatResponseHandler> provider,
            Consumer<StreamingChatResponseHandler> script) {
        return new LlmStreamingClientFactory<>() {
            @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return model(); }
            @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return model(); }

            private StreamingChatModel model() {
                return new StreamingChatModel() {
                    @Override public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                        provider.set(handler);
                        script.accept(handler);
                    }
                };
            }
        };
    }

    static Path repoRoot() {
        Path current = Path.of(".").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve(".git"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repo root not found");
        }
        return current;
    }
}
