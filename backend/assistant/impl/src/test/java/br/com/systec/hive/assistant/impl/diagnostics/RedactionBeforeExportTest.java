package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import dev.langchain4j.data.message.AiMessage;
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

/** RED-06: o objeto entregue ao sink já contém só texto redigido, antes de qualquer exportação. */
class RedactionBeforeExportTest {

    @Test
    void when_spanReachesSink_then_itCarriesOnlyRedactedText() {
        List<List<ChatSpan>> exported = new ArrayList<>();
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", exported::add);
        ChatModel model = new FixtureModel();
        new SpanListeningChatModel(model, new ProviderCallSpanListener())
                .chat(ChatRequest.builder().messages(UserMessage.from(SensitiveFixture.RAW)).build());
        ChatTurnSpanScope.close(ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini")
                .build());

        String payload = exported.toString();
        for (String literal : SensitiveFixture.LITERALS) {
            assertThat(payload).doesNotContain(literal);
        }
        assertThat(payload).contains("[REDACTED:");
    }

    @Test
    void when_responseIsEmpty_then_noTextKeyIsInvented() {
        List<List<ChatSpan>> exported = new ArrayList<>();
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", exported::add);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(null, null, null);
        ChatTurnSpanScope.close(null);

        List<ChatSpan> children = exported.getFirst().stream().filter(s -> !s.rootObservation()).toList();
        assertThat(children).hasSize(1);
        assertThat(children.getFirst().attributes())
                .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
    }

    private static final class FixtureModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from(SensitiveFixture.RAW)).build();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }
    }
}
