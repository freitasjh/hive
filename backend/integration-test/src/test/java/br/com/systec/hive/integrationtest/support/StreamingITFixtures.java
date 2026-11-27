package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.port.TurnPromptPolicy;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import br.com.systec.hive.ai.impl.SessionGateRegistry;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared seams for the ADR-001 integration tests: fake provider at the boundary, real gateways. */
public final class StreamingITFixtures {

    private StreamingITFixtures() {
    }

    public static TurnRequest request(UUID session) {
        return new TurnRequest(session, new TurnPrompt(UUID.randomUUID(), "system", 0.3, 128, 0.9),
                AiProvider.OPENAI, "model-x", "key", null, List.of(UserMessage.from("question")));
    }

    public static PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("system");
        template.setInferenceParams(new InferenceParams(0.3, 0.9, 128));
        return template;
    }

    /** The real assistant policy: a blank template must fall back to AssistantTurnGateway.PROMPT. */
    public static TurnPromptPolicy policy() {
        return text -> text == null || text.isBlank() ? AssistantTurnGateway.PROMPT : text;
    }

    public static ScheduledExecutorService scheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    public static Semaphore gate(UUID session) {
        return SessionGateRegistry.shared().gateFor(session);
    }

    public static LlmStreamingClientFactory<StreamingChatModel> factory(
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

    public static AssistantTurnGateway syncGateway(ChatModel model) {
        LlmClientFactory<ChatModel> factory = mock(LlmClientFactory.class);
        when(factory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(model);
        return new AssistantTurnGateway(factory);
    }

    public static ChatResponse response(String text) {
        return ChatResponse.builder()
                .aiMessage(dev.langchain4j.data.message.AiMessage.from(text))
                .finishReason(dev.langchain4j.model.output.FinishReason.STOP)
                .build();
    }

    public static Path repoRoot() {
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
