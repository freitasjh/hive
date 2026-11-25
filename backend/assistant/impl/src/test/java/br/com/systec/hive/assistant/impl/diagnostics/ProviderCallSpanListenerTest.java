package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCallSpanListenerTest {

    private final List<List<ChatSpan>> published = new ArrayList<>();
    private final ChatSpanSink sink = published::add;

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("pergunta")).build();
    }

    private static ChatResponse response() {
        return ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("resposta")).build();
    }

    @Test
    void when_modelAnswers_then_interfaceCallbacksProduceOneGenerationChild() {
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", sink);
        ChatModel model = new SpanListeningChatModel(new StaticChatModel(response()), new ProviderCallSpanListener());

        ChatResponse answer = model.chat(request());
        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini")
                .build());

        assertThat(answer).isNotNull();
        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(2);
        assertThat(spans.get(1).name()).isEqualTo("chat.provider.call");
        assertThat(spans.get(1).attributes()).containsEntry("langfuse.observation.level", "DEFAULT");
    }

    @Test
    void when_modelThrows_then_onErrorStillClosesTheChildAsError() {
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", sink);
        ChatModel model = new SpanListeningChatModel(new ThrowingChatModel(), new ProviderCallSpanListener());

        assertThatThrownBy(() -> model.chat(request())).isInstanceOf(RuntimeException.class);

        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini")
                .build());

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(2);
        assertThat(spans.get(1).attributes())
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "IllegalStateException:unexpected");
    }

    private static final class StaticChatModel implements ChatModel {
        private final ChatResponse response;

        private StaticChatModel(ChatResponse response) {
            this.response = response;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return response;
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }
    }

    private static final class ThrowingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            throw new IllegalStateException("SEGREDO do provedor");
        }
    }
}
