package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.port.ChatTurnGateway;
import br.com.systec.hive.assistant.impl.ai.AssistantDefault;
import br.com.systec.hive.assistant.impl.util.ProviderMessages;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metade estável do gateway (ADR-020 §5.2): memória por sessão, serialização e regra do system.
 *
 * <p>A concreta fica com a política de turno — retry ({@link br.com.systec.hive.provider.langchain4j.LlmRetryPolicy}),
 * cliente por turno ({@link br.com.systec.hive.provider.api.LlmClientFactory}) e a leitura das métricas de
 * {@link Result}. Aqui ficam só as decisões que não podem divergir entre os dois chamadores de
 * {@link ChatTurnGateway}: o prompt é o da sessão ou o fallback, o histórico vem de
 * {@code TurnRequest.conversation()} (JPA) e nunca uma chamada ao proxy corre ao mesmo tempo para o
 * mesmo {@code sessionId}.
 *
 * <p><h3>Memória não é histórico</h3>
 * <p>O store é volátil e em processo. A cada turno ele é <em>reconstruído</em> a partir do que o JPA
 * já havia renderizado em {@code conversation()} — o {@code history/} continua sendo a fonte de
 * verdade (ADR-017). Isso mantém a sequência K4 byte a byte igual à do gateway antigo, inclusive
 * depois de reinícios, e evita duplicar mensagens quando a memória e o JPA divergem.
 *
 * <p><h3>A8 — concorrência</h3>
 * <p>O javadoc do AiServices 1.19.0 é explícito: chamar o mesmo AI Service simultaneamente para o
 * mesmo {@code @MemoryId} corrompe o {@link ChatMemory}. O lock por sessão é a única proteção;
 * o {@code ChatMemoryProvider} é síncrono e não cria callback assíncrono ({@code TenantContext}
 * usa {@code ScopedValue}, que não propaga — K9).
 */
public abstract class AbstractLangChain4jTurnGateway implements ChatTurnGateway {

    /** Window grande o suficiente para o findLast20 da UI mais system + user, sem evicção. */
    static final int MAX_MEMORY_MESSAGES = 64;

    private final ChatMemoryProvider chatMemoryProvider;
    private final ConcurrentHashMap<UUID, Object> sessionLocks = new ConcurrentHashMap<>();

    protected AbstractLangChain4jTurnGateway() {
        this(new SessionChatMemoryProvider());
    }

    protected AbstractLangChain4jTurnGateway(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemoryProvider = chatMemoryProvider;
    }

    /** Constrói o proxy com o cliente deste turno. Implementado na concreta via AiServices.builder. */
    protected abstract AssistantDefault proxyFor(ChatModel client);

    /** Extrai o {@link TurnReply} (incluindo as métricas de {@link Result}) na concreta. */
    protected abstract TurnReply toReply(Result<String> result, int promptMessages, int promptChars);

    /**
     * Regra de precedência: template da sessão > {@link AssistantDefault#PROMPT}.
     * Um template em branco conta como ausente — {@code SystemMessage} vazio instrui o modelo a ser
     * breve, o que a conversa não pediu.
     */
    protected final String systemFor(PromptTemplate template) {
        String text = template.getTemplate();
        return text == null || text.isBlank() ? AssistantDefault.PROMPT : text;
    }

    /** A última mensagem renderizada do turno é o {@code @UserMessage}; o resto é histórico. */
    protected final String userFor(TurnRequest request) {
        List<ChatMessage> conversation = request.conversation();
        if (conversation.isEmpty()) {
            return "";
        }
        String text = ProviderMessages.textOf(conversation.getLast());
        return text == null ? "" : text;
    }

    protected final List<ChatMessage> priorHistoryFor(TurnRequest request) {
        List<ChatMessage> conversation = request.conversation();
        if (conversation.size() <= 1) {
            return List.of();
        }
        return new ArrayList<>(conversation.subList(0, conversation.size() - 1));
    }

    /** O prompt exato que será enviado — usado só para as medidas do turno (nunca logado). */
    protected final List<ChatMessage> telemetryPrompt(String system, List<ChatMessage> prior, String user) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        messages.addAll(prior);
        messages.add(new UserMessage(user));
        return messages;
    }

    protected final Object lockFor(UUID sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
    }

    /**
     * Reconstrói a memória volátil a partir do histórico persistido — chamada a CADA tentativa.
     *
     * <p>{@link ChatMemory#set(Iterable)} substitui todas as mensagens, mas o {@code clear()} explícito
     * deixa a intenção irrecusável: cada tentativa do retry deve começar com o histórico do JPA e sem
     * o system/user que o AiService acrescentou na tentativa anterior. Sem isso, o segundo attempt
     * veria {@code [system, histórico, user, user]} e as contagens reconstruídas divergiriam do prompt
     * realmente enviado (K4/K5).
     *
     * <p>O provedor devolve {@link MessageWindowChatMemory}, cujo {@code set(List)} aceita lista vazia
     * — contrário ao default de {@code ChatMemory.set}, que exige ao menos uma mensagem.
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
     * Provider em memória, compartilhado por todos os proxies da aplicação.
     *
     * <p>Cada proxy AiService guarda seu próprio mapa de memórias; como o cliente é criado por turno,
     * um provider que guardasse as mensagens dentro de si perderia o histórico entre turnos. O
     * {@link InMemoryChatMemoryStore} é o estado compartilhado —{@link MessageWindowChatMemory} continua
     * aplicando a janela e a regra de system-first sobre ele.
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
