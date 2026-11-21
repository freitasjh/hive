package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.impl.util.ProviderMessages;
import br.com.systec.hive.provider.api.AiProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI-01 — baseline de caracterização (K4 ordem + K5 métricas), capturada ANTES da migração AiService.
 *
 * <p>Teste JUnit puro (sem Spring, sem banco) — é a rede de segurança da ADR-020, não uma IT. Não usa
 * Mockito no modelo: usa um {@link RecordingChatModel} que implementa {@code doChat(ChatRequest)}, o
 * único ponto por onde passam tanto a chamada antiga ({@code client.chat(prompt)} → default
 * {@code chat(List)} → {@code chat(ChatRequest)}) quanto a nova (o proxy AiService chama
 * {@code chat(ChatRequest)}). Assim o MESMO teste roda antes e depois da troca e prova que a sequência
 * e os números não mudaram. Tokens/finishReason vêm de {@code Result}; as contagens de prompt são
 * reconstruídas pelo gateway a partir de system + histórico + user.
 *
 * <p>Valores fixados nesta execução (2026-11-21): promptMessages=4, promptChars=59,
 * promptTokens=120, completionTokens=340, totalTokens=460, finishReason=STOP.
 */
class AiServiceCharacterizationTest {

    private static final String TEMPLATE = "Voce e um assistente.";
    private static final String OLD_USER = "pergunta antiga";
    private static final String OLD_ANSWER = "resposta antiga";
    private static final String USER = "pergunta";
    private static final String ANSWER = "resposta";

    @Test
    void when_templateAndHistory_then_systemLeadsExactSequenceAndMetricsAreMeasured() {
        RecordingChatModel model = new RecordingChatModel(
                ChatResponse.builder()
                        .aiMessage(AiMessage.from(ANSWER))
                        .tokenUsage(new TokenUsage(120, 340, 460))
                        .finishReason(FinishReason.STOP)
                        .build());

        TurnReply reply = gateway(model).execute(request(TEMPLATE));

        List<ChatMessage> prompt = model.lastMessages();
        assertThat(prompt).hasSize(4);
        assertThat(prompt.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) prompt.get(0)).text()).isEqualTo(TEMPLATE);
        assertThat(prompt.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) prompt.get(1)).singleText()).isEqualTo(OLD_USER);
        assertThat(prompt.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) prompt.get(2)).text()).isEqualTo(OLD_ANSWER);
        assertThat(prompt.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) prompt.get(3)).singleText()).isEqualTo(USER);

        assertThat(ProviderMessages.totalChars(prompt)).isEqualTo(59);
        assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
        assertThat(reply.text()).isEqualTo(ANSWER);
        assertThat(reply.promptMessages()).isEqualTo(4);
        assertThat(reply.promptChars()).isEqualTo(59);
        assertThat(reply.promptTokens()).isEqualTo(120);
        assertThat(reply.completionTokens()).isEqualTo(340);
        assertThat(reply.totalTokens()).isEqualTo(460);
        assertThat(reply.finishReason()).isEqualTo("STOP");
        assertThat(reply.failure()).isNull();
    }

    @Test
    void when_reasoningModelReturnsNoText_then_emptyReplyKeepsFinishReasonAndMetrics() {
        RecordingChatModel model = new RecordingChatModel(
                ChatResponse.builder()
                        .aiMessage(AiMessage.builder().text(null).build())
                        .tokenUsage(new TokenUsage(10, 32, 42))
                        .finishReason(FinishReason.LENGTH)
                        .build());

        TurnReply reply = gateway(model).execute(request(TEMPLATE));

        assertThat(reply.status()).isEqualTo(TurnReply.Status.EMPTY_RESPONSE);
        assertThat(reply.text()).isNull();
        assertThat(reply.finishReason()).isEqualTo("LENGTH");
        assertThat(reply.promptTokens()).isEqualTo(10);
        assertThat(reply.completionTokens()).isEqualTo(32);
        assertThat(reply.totalTokens()).isEqualTo(42);
        assertThat(reply.promptMessages()).isEqualTo(4);
        assertThat(reply.promptChars()).isEqualTo(59);
    }

    /**
     * REGRESSÃO (review CHANGES_REQUIRED): o AiService grava system + user na ChatMemory a cada
     * chamada. Sem re-sementar dentro do retry, o 2º attempt enviaria o user duplicado e
     * {@code promptMessages}/{@code promptChars} (calculados antes do retry) divergiriam do prompt
     * real. O 2º ChatRequest capturado precisa ser idêntico ao baseline AI-01.
     */
    @Test
    void when_firstAttemptIsRateLimited_then_retrySendsTheExactBaselinePrompt() {
        ChatResponse success = ChatResponse.builder()
                .aiMessage(AiMessage.from(ANSWER))
                .tokenUsage(new TokenUsage(120, 340, 460))
                .finishReason(FinishReason.STOP)
                .build();
        RetryChatModel model = new RetryChatModel(success);
        AssistantTurnGateway gateway = new AssistantTurnGateway(
                new FixedClientFactory(model), br.com.systec.hive.provider.langchain4j.LlmRetryPolicy.withoutDelay());

        TurnReply reply = gateway.execute(request(TEMPLATE));

        assertThat(model.calls()).as("429 é transitivo: exatamente duas tentativas").isEqualTo(2);
        List<ChatMessage> second = model.requestAt(1);
        assertThat(second).hasSize(4);
        assertThat(second.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) second.get(0)).text()).isEqualTo(TEMPLATE);
        assertThat(second.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) second.get(1)).singleText()).isEqualTo(OLD_USER);
        assertThat(second.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) second.get(2)).text()).isEqualTo(OLD_ANSWER);
        assertThat(second.get(3)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) second.get(3)).singleText()).isEqualTo(USER);
        assertThat(ProviderMessages.totalChars(second)).isEqualTo(59);

        assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
        assertThat(reply.promptMessages()).isEqualTo(4);
        assertThat(reply.promptChars()).isEqualTo(59);
        assertThat(reply.promptTokens()).isEqualTo(120);
        assertThat(reply.completionTokens()).isEqualTo(340);
        assertThat(reply.totalTokens()).isEqualTo(460);
        assertThat(reply.finishReason()).isEqualTo("STOP");
    }

    private static AssistantTurnGateway gateway(RecordingChatModel model) {
        return new AssistantTurnGateway(new FixedClientFactory(model));
    }

    private static TurnRequest request(String template) {
        PromptTemplate promptTemplate = new PromptTemplate();
        promptTemplate.setId(UUID.randomUUID());
        promptTemplate.setTemplate(template);
        promptTemplate.setInferenceParams(new InferenceParams(0.7, null, null));
        List<dev.langchain4j.data.message.ChatMessage> conversation = List.of(
                new UserMessage(OLD_USER), new AiMessage(OLD_ANSWER), new UserMessage(USER));
        return new TurnRequest(UUID.randomUUID(), TurnPrompts.from(promptTemplate), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test", null, conversation);
    }

    /** ChatModel concreto mínimo: registra o ChatRequest final, Serve aos dois caminhos (antigo e novo). */
    private static final class RecordingChatModel implements ChatModel {
        private final ChatResponse response;
        private final List<List<ChatMessage>> requests = new ArrayList<>();

        private RecordingChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest.messages());
            return response;
        }

        private List<ChatMessage> lastMessages() {
            assertThat(requests).as("o modelo deve ter sido chamado exatamente uma vez").hasSize(1);
            return requests.get(0);
        }
    }

    /** Mesmo padrão do {@link RecordingChatModel}, mas o 1º doChat lança 429 (transiente). */
    private static final class RetryChatModel implements ChatModel {
        private final ChatResponse response;
        private final List<List<ChatMessage>> requests = new ArrayList<>();

        private RetryChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest.messages());
            if (requests.size() == 1) {
                throw new RateLimitException("429 from provider");
            }
            return response;
        }

        private int calls() {
            return requests.size();
        }

        private List<ChatMessage> requestAt(int index) {
            return requests.get(index);
        }
    }

    private static final class FixedClientFactory implements br.com.systec.hive.provider.api.LlmClientFactory<ChatModel> {
        private final ChatModel model;

        private FixedClientFactory(ChatModel model) {
            this.model = model;
        }

        @Override
        public ChatModel createClient(br.com.systec.hive.provider.api.AiProvider provider, String modelName,
                                      Double temperature, Integer maxTokens, Double topP,
                                      String apiKey, String baseUrl) {
            return model;
        }
    }
}
