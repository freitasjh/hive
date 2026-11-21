package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A8 / NFR-02: o javadoc do AiServices 1.19.0 proíbe chamadas concorrentes ao mesmo {@code @MemoryId}
 * (corrompe {@code ChatMemory}). O gateway serializa por {@code sessionId}; este teste falharia sem
 * o lock porque as duas chamadas entrariam no modelo ao mesmo tempo.
 */
class ChatMemorySerializationTest {

    @Test
    void when_twoThreadsShareASession_then_theProxyIsNeverEnteredConcurrently() throws Exception {
        ProbingChatModel model = new ProbingChatModel();
        LlmClientFactory<ChatModel> factory = new FixedClientFactory(model);
        AssistantTurnGateway gateway = new AssistantTurnGateway(factory);
        UUID sessionId = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<TurnReply> first = pool.submit(() -> gateway.execute(request(sessionId, "pergunta 1")));
            Future<TurnReply> second = pool.submit(() -> gateway.execute(request(sessionId, "pergunta 2")));

            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(TurnReply.Status.ANSWERED);
            assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(TurnReply.Status.ANSWERED);
        } finally {
            pool.shutdownNow();
        }

        assertThat(model.maxConcurrentCalls()).as("no máximo uma chamada ao proxy por sessionId")
                .isEqualTo(1);
        assertThat(model.calls()).isEqualTo(2);
    }

    @Test
    void when_twoTurnsShareASession_then_memoryIsReseededInsteadOfDuplicated() {
        ProbingChatModel model = new ProbingChatModel();
        AssistantTurnGateway gateway = new AssistantTurnGateway(new FixedClientFactory(model));
        UUID sessionId = UUID.randomUUID();

        // Turno 2 vem com o histórico já persistido pelo JPA (usuário + assistente do turno 1).
        gateway.execute(request(sessionId, "pergunta 1"));
        TurnReply second = gateway.execute(request(sessionId, "pergunta 2",
                List.of(new UserMessage("pergunta 1"), new AiMessage("resposta 1"))));

        assertThat(second.status()).isEqualTo(TurnReply.Status.ANSWERED);
        List<ChatMessage> lastPrompt = model.lastPrompt();
        assertThat(lastPrompt).hasSize(4);
        assertThat(lastPrompt.get(0)).isInstanceOf(dev.langchain4j.data.message.SystemMessage.class);
        assertThat(((dev.langchain4j.data.message.SystemMessage) lastPrompt.get(0)).text())
                .isEqualTo("template da sessao");
    }

    private static TurnRequest request(UUID sessionId, String text) {
        return request(sessionId, text, List.of());
    }

    private static TurnRequest request(UUID sessionId, String text, List<ChatMessage> prior) {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("template da sessao");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        List<ChatMessage> conversation = new java.util.ArrayList<>(prior);
        conversation.add(new UserMessage(text));
        return new TurnRequest(sessionId, TurnPrompts.from(template), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test", null, conversation);
    }

    /** Sinaliza sobreposição sem {@code Thread.sleep()}: um latch curto dentro do próprio modelo. */
    private static final class ProbingChatModel implements ChatModel {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger max = new AtomicInteger();
        private final AtomicInteger calls = new AtomicInteger();
        private final List<ChatMessage> lastPrompt = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            int now = active.incrementAndGet();
            max.accumulateAndGet(now, Math::max);
            calls.incrementAndGet();
            lastPrompt.clear();
            lastPrompt.addAll(chatRequest.messages());
            try {
                new CountDownLatch(1).await(150, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("resposta"))
                    .build();
        }

        @Override
        public dev.langchain4j.model.chat.request.ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.EMPTY;
        }

        private int maxConcurrentCalls() {
            return max.get();
        }

        private int calls() {
            return calls.get();
        }

        private List<ChatMessage> lastPrompt() {
            return lastPrompt;
        }
    }

    private static final class FixedClientFactory implements LlmClientFactory<ChatModel> {
        private final ChatModel model;

        private FixedClientFactory(ChatModel model) {
            this.model = model;
        }

        @Override
        public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                      Integer maxTokens, Double topP, String apiKey, String baseUrl) {
            return model;
        }
    }
}
