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

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-02: texto vive no portador de turno do ADR-001, nunca em ChatTrace. */
class TextCarrierListenerTest {

    private final List<List<ChatSpan>> published = new ArrayList<>();

    @Test
    void when_callbacksReceiveSensitiveText_then_carrierHoldsRedactedTextOutsideChatTrace() {
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();
        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);
        ChatModel model = new SpanListeningChatModel(new FixtureModel(SensitiveFixture.RAW),
                new ProviderCallSpanListener());

        model.chat(ChatRequest.builder().messages(UserMessage.from(SensitiveFixture.RAW)).build());
        ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini").build());

        List<ChatSpan> spans = published.getFirst();
        ChatSpan child = spans.stream().filter(span -> !span.rootObservation()).findFirst().orElseThrow();
        assertThat(child.attributes())
                .containsEntry("langfuse.observation.input", SensitiveFixture.REDACTED)
                .containsEntry("langfuse.observation.output", SensitiveFixture.REDACTED);
        assertThat(spans.stream().filter(ChatSpan::rootObservation).findFirst().orElseThrow().attributes())
                .doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
        assertThat(ChatTrace.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("prompt", "response", "promptText", "responseText");
    }

    @Test
    void when_twoAttemptsRun_then_eachAttemptGetsItsOwnRedactedText() {
        UUID session = UUID.randomUUID();
        ChatTurnSpanScope.open(session, UUID.randomUUID(), AiProvider.OPENAI, "gpt-4o-mini", published::add);
        TextRedactor redactor = new TextRedactor();

        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput("CPF 123.456.789-09");
        redactor.captureOutput("joao.silva@example.com");
        ChatTurnSpanScope.attemptSucceeded(1, 2, 3);

        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput("senha=SuperSecret123");
        redactor.captureOutput("(11) 98765-4321");
        ChatTurnSpanScope.attemptSucceeded(4, 5, 9);

        ChatTurnSpanScope.close(null);
        List<ChatSpan> children = published.getFirst().stream().filter(s -> !s.rootObservation()).toList();
        assertThat(children).hasSize(2);
        assertThat(children.get(0).attributes())
                .containsEntry("langfuse.observation.input", "CPF [REDACTED:cpf]")
                .containsEntry("langfuse.observation.output", "[REDACTED:email]");
        assertThat(children.get(1).attributes())
                .containsEntry("langfuse.observation.input", "senha [REDACTED:secret]")
                .containsEntry("langfuse.observation.output", "[REDACTED:phone]");
    }

    private static final class FixtureModel implements ChatModel {
        private final String answer;

        private FixtureModel(String answer) {
            this.answer = answer;
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }
    }
}
