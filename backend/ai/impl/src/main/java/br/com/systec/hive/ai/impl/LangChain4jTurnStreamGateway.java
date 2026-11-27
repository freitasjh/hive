package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.AssistantStreamDefault;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnPromptPolicy;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.ai.impl.util.ProviderMessages;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Streaming turn gateway (ADR-001 D5/D6). No {@code .chatModel(...)} here: the proxy is built with
 * {@code streamingChatModel(...)} exclusively (S1).
 */
public final class LangChain4jTurnStreamGateway implements TurnStreamGateway {

    private final LlmStreamingClientFactory<StreamingChatModel> clientFactory;
    private final TurnPromptPolicy promptPolicy;
    private final SessionGateRegistry gates = SessionGateRegistry.shared();
    private final StreamingRetryPolicy retryPolicy = StreamingRetryPolicy.defaults();
    private final StreamingMetrics metrics;
    private final ScheduledExecutorService scheduler;
    private final InMemoryChatMemoryStore memoryStore = new InMemoryChatMemoryStore();
    private final long releaseWindowMillis;

    public LangChain4jTurnStreamGateway(LlmStreamingClientFactory<StreamingChatModel> clientFactory,
            TurnPromptPolicy promptPolicy, StreamingMetrics metrics, ScheduledExecutorService scheduler) {
        this(clientFactory, promptPolicy, metrics, scheduler,
                StreamingTurnHandleImpl.CANCEL_DEGRADED_RELEASE_WINDOW_MS);
    }

    /** Short window is a test seam; production always uses the frozen 5_000 ms. */
    LangChain4jTurnStreamGateway(LlmStreamingClientFactory<StreamingChatModel> clientFactory,
            TurnPromptPolicy promptPolicy, StreamingMetrics metrics, ScheduledExecutorService scheduler,
            long releaseWindowMillis) {
        this.clientFactory = clientFactory;
        // Fail fast: without the caller's policy the streaming prompt would diverge from the sync one.
        this.promptPolicy = Objects.requireNonNull(promptPolicy, "promptPolicy");
        this.metrics = metrics;
        this.scheduler = scheduler;
        this.releaseWindowMillis = releaseWindowMillis;
    }

    @Override
    public StreamingTurnHandle stream(TurnRequest request, Consumer<TurnStreamEvent> sink) {
        UUID session = request.sessionId();
        try {
            gates.acquire(session); // AT-01: refcounted, entry evicted when the turn is done
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // WF6: restore the flag; acquire discarded its ref
            sink.accept(new TurnStreamEvent.Failed("cancelled", e));
            return () -> { };
        }

        StreamingTurnHandleImpl handle = new StreamingTurnHandleImpl(sink, () -> gates.release(session),
                metrics, scheduler, releaseWindowMillis);
        StreamingRetryPolicy.Context context = retryPolicy.capture(request.sessionId(), UUID.randomUUID());
        startAttempt(request, handle, context, sink, 1);
        return handle;
    }

    private void startAttempt(TurnRequest request, StreamingTurnHandleImpl handle,
            StreamingRetryPolicy.Context context, Consumer<TurnStreamEvent> sink, int attempt) {
        AtomicBoolean emitted = new AtomicBoolean();
        AtomicBoolean retryStarted = new AtomicBoolean();
        Consumer<TurnStreamEvent> routed = event -> {
            if (event instanceof TurnStreamEvent.Reasoning || event instanceof TurnStreamEvent.Text) {
                emitted.set(true);
            }
            if (event instanceof TurnStreamEvent.Failed failed
                    && retryPolicy.shouldRetry(failed.cause(), emitted.get(), attempt)
                    && retryStarted.compareAndSet(false, true)) {
                startAttempt(request, handle, context, sink, attempt + 1);
                return; // nothing reached the client: retry is invisible to the sink
            }
            handle.emit(event);
        };
        TurnStreamEventMapper mapper = TurnStreamEventMapper.of(routed);

        try {
            StreamingChatModel client = clientFactory.createStreamingClientWithSession(request.provider(),
                    request.model(), request.promptTemplate().temperature(), request.promptTemplate().maxTokens(),
                    request.promptTemplate().topP(), request.apiKey(), request.baseUrl(), context.sessionId());
            AssistantStreamDefault proxy = AiServices.builder(AssistantStreamDefault.class)
                    .streamingChatModel(client)
                    .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(64)
                            .chatMemoryStore(memoryStore).alwaysKeepSystemMessageFirst(true).build())
                    .build();
            seedMemory(request);
            // AT-02: same system rule as the sync path; ai/** only knows the port, never the fallback text.
            String system = promptPolicy.systemFor(request.promptTemplate().text());
            TokenStream stream = proxy.chat(context.sessionId(), system, lastUserText(request));
            mapper.attachTo(stream, info -> handle.providerHandle(info.streamingHandle()));
            stream.start();
        } catch (RuntimeException failure) {
            routed.accept(new TurnStreamEvent.Failed(StreamFailureCodes.of(failure), failure));
        }
    }

    private void seedMemory(TurnRequest request) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(request.sessionId()).maxMessages(64).chatMemoryStore(memoryStore)
                .alwaysKeepSystemMessageFirst(true).build();
        memory.clear();
        memory.set(prior(request.conversation()));
    }

    private static List<ChatMessage> prior(List<ChatMessage> conversation) {
        return conversation.size() <= 1 ? List.of() : List.copyOf(conversation.subList(0, conversation.size() - 1));
    }

    private static String lastUserText(TurnRequest request) {
        List<ChatMessage> conversation = request.conversation();
        if (conversation.isEmpty()) {
            return "";
        }
        String text = ProviderMessages.textOf(conversation.get(conversation.size() - 1));
        return text == null ? "" : text;
    }
}
