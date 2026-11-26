package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** MET-09/RF-10: provider fake que expõe cachedTokens alimenta o atributo normalizado na generation. */
class CachedTokenUsageTest {

    /** Fake no estilo OpenAI: expõe `inputTokensDetails().cachedTokens()` como o SDK 1.19.0. */
    static final class FakeOpenAiUsage extends TokenUsage {
        private final InputTokensDetails details;

        FakeOpenAiUsage(int input, int cached, int output, int total) {
            super(input, output, total);
            this.details = new InputTokensDetails(cached);
        }

        public InputTokensDetails inputTokensDetails() {
            return details;
        }
    }

    public static final class InputTokensDetails {
        private final Integer cachedTokens;

        InputTokensDetails(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
        }

        public Integer cachedTokens() {
            return cachedTokens;
        }
    }

    @Test
    void when_providerExposesCache_then_generationCarriesExclusiveBucketsAndCanonicalModelKey() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();
        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);

        ProviderCallSpanListener listener = new ProviderCallSpanListener();
        ChatTurnSpanScope.attemptStarted();
        listener.onResponse(new dev.langchain4j.model.chat.listener.ChatModelResponseContext(
                ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("ok"))
                        .tokenUsage(new FakeOpenAiUsage(17903, 17817, 188, 18091))
                        .finishReason(dev.langchain4j.model.output.FinishReason.STOP).build(),
                ChatRequest.builder().messages(new dev.langchain4j.data.message.UserMessage("pergunta")).build(),
                dev.langchain4j.model.ModelProvider.OPEN_AI,
                java.util.Map.of()));
        ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini").build());

        ChatSpan child = published.getFirst().stream().filter(span -> !span.rootObservation())
                .findFirst().orElseThrow();
        assertThat(child.attributes())
                .containsEntry("langfuse.observation.type", "generation")
                .containsEntry("langfuse.observation.model.name", "gpt-4o-mini")
                .containsEntry("langfuse.observation.usage_details",
                        "{\"input\":86,\"output\":188,\"input_cached_tokens\":17817,\"total\":18091}");
        assertThat(child.attributes().keySet()).doesNotContain(
                "langfuse.observation.cost_details", "gen_ai.usage.cost", "costDetails");
    }

    @Test
    void when_providerHasNoCache_then_onlyThreeBucketsAreSerialized() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();
        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptSucceeded(10, 5, 15);
        ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini").build());

        ChatSpan child = published.getFirst().stream().filter(span -> !span.rootObservation())
                .findFirst().orElseThrow();
        assertThat(child.attributes().get("langfuse.observation.usage_details"))
                .isEqualTo("{\"input\":10,\"output\":5,\"total\":15}");
    }
}
