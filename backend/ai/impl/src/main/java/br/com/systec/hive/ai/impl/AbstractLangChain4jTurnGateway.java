package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.AssistantDefault;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Stable half of the gateway (ADR-020 §5.2, moved to {@code ai/impl} by ADR-021): session-scoped
 * memory, serialization and prompt measurement.
 *
 * <p>The concrete subclass owns turn policy — retry, per-turn client creation, system/user text and
 * {@link Result} reading. What stays here cannot diverge between callers of {@link ChatTurnGateway}:
 * one {@code ChatMemory} per {@code @MemoryId}, one turn at a time for that id, and a prompt rebuilt
 * from {@code TurnRequest.conversation()} on every attempt.
 *
 * <h3>Memory is not the persisted conversation</h3>
 *
 * <p>The store is volatile and in-process. Each turn re-seeds it from the provider messages the
 * caller already rendered from the persisted conversation (ADR-017/020), so the K4 sequence stays
 * byte-for-byte identical across restarts and no duplicate message appears when memory and the
 * database disagree.
 *
 * <h3>A8 — concurrency</h3>
 *
 * <p>The AiServices 1.19.0 javadoc is explicit: calling the same AI Service concurrently for the
 * same {@code @MemoryId} corrupts the {@link ChatMemory}. The shared {@link Semaphore} per session id
 * is the only protection; the {@code ChatMemoryProvider} is synchronous and creates no async callback
 * ({@code TenantContext} uses {@code ScopedValue}, which does not propagate — K9).
 */
public abstract class AbstractLangChain4jTurnGateway implements ChatTurnGateway {

    /** Window large enough for the UI's findLast20 plus system and user, without eviction. */
    static final int MAX_MEMORY_MESSAGES = 64;

    private final ChatMemoryProvider chatMemoryProvider;
    private final SessionGateRegistry gates = SessionGateRegistry.shared();

    protected AbstractLangChain4jTurnGateway() {
        this(new SessionChatMemoryProvider());
    }

    protected AbstractLangChain4jTurnGateway(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
    }

    /** Builds the proxy with this turn's client. Implemented in the concrete via AiServices.builder. */
    protected abstract AssistantDefault proxyFor(ChatModel client);

    /** Extracts the {@link TurnReply} (including {@link Result} metrics) in the concrete. */
    protected abstract TurnReply toReply(Result<String> result, int promptMessages, int promptChars);

    /** The exact prompt to be measured — never logged. */
    protected final List<ChatMessage> telemetryPrompt(String system, List<ChatMessage> prior, String user) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.addAll(prior);
        messages.add(new UserMessage(user));
        return messages;
    }

    /** RF8: the sync path takes the same owner-free permit the streaming path takes before start(). */
    protected final Semaphore gateFor(UUID sessionId) {
        return gates.gateFor(sessionId);
    }

    /** AT-01: turns must go through the refcounted acquire/release, not the inspection view above. */
    protected final SessionGateRegistry gateRegistry() {
        return gates;
    }

    /**
     * Rebuilds the volatile memory from the rendered conversation — called on EVERY attempt.
     *
     * <p>{@link ChatMemory#set(Iterable)} replaces every message, but the explicit {@code clear()}
     * makes the intent undeniable: each retry attempt must start from the caller's messages and
     * without the system/user the AiService appended on the previous attempt. Otherwise the second
     * attempt would see {@code [system, prior, user, user]} and the precomputed prompt measurements
     * would no longer describe the prompt actually sent (K4/K5).
     *
     * <p>The provider returns {@link MessageWindowChatMemory}, whose {@code set(List)} accepts an
     * empty list — unlike the default {@code ChatMemory.set}, which requires at least one message.
     */
    protected final void seedMemory(UUID sessionId, List<ChatMessage> prior) {
        ChatMemory memory = chatMemoryProvider.get(sessionId);
        memory.clear();
        memory.set(prior);
    }

    protected final ChatMemoryProvider chatMemoryProvider() {
        return chatMemoryProvider;
    }

    /**
     * Provider shared by every proxy in the application.
     *
     * <p>Each AiService proxy keeps its own memory map; because the client is created per turn, a
     * provider that kept messages inside itself would lose the conversation between turns. The
     * {@link InMemoryChatMemoryStore} is the shared state — {@link MessageWindowChatMemory} still
     * applies the window and the system-first rule on top of it.
     */
    private static final class SessionChatMemoryProvider implements ChatMemoryProvider {
        private final InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();

        @Override
        public ChatMemory get(Object memoryId) {
            return MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(MAX_MEMORY_MESSAGES)
                    .chatMemoryStore(store)
                    .alwaysKeepSystemMessageFirst(true)
                    .build();
        }
    }
}
