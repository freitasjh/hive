package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnPrompt;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** AT-02: streaming prompt must use the same system policy and user text as the sync turn. */
class StreamingPromptParityIT {

    @Test
    void when_templateTextIsBlank_then_bothPathsUseTheSameFallbackSystem() {
        assertParity("   ", AssistantTurnGateway.PROMPT);
    }

    @Test
    void when_templateTextIsPresent_then_bothPathsUseItVerbatim() {
        assertParity("Instrução exclusiva do tenant.", "Instrução exclusiva do tenant.");
    }

    private static void assertParity(String templateText, String expectedSystem) {
        UUID session = UUID.randomUUID();
        TurnRequest request = new TurnRequest(session,
                new TurnPrompt(UUID.randomUUID(), templateText, 0.3, 128, 0.9),
                br.com.systec.hive.provider.api.AiProvider.OPENAI, "model-x", "key", null,
                List.of(UserMessage.from("question")));

        ChatModel model = mock(ChatModel.class);
        lenient().when(model.defaultRequestParameters()).thenReturn(DefaultChatRequestParameters.EMPTY);
        lenient().when(model.listeners()).thenReturn(List.of());
        AtomicReference<ChatRequest> syncPrompt = new AtomicReference<>();
        when(model.chat(any(ChatRequest.class))).thenAnswer(invocation -> {
            syncPrompt.set(invocation.getArgument(0));
            return ChatResponse.builder()
                    .aiMessage(dev.langchain4j.data.message.AiMessage.from("answer"))
                    .finishReason(FinishReason.STOP).build();
        });
        StreamingITFixtures.syncGateway(model).execute(request);

        AtomicReference<ChatRequest> streamPrompt = new AtomicReference<>();
        StreamingChatModel streamingModel = new StreamingChatModel() {
            @Override public void doChat(ChatRequest prompt, StreamingChatResponseHandler handler) {
                streamPrompt.set(prompt);
            }
        };
        LlmStreamingClientFactory<StreamingChatModel> streamingFactory = new LlmStreamingClientFactory<>() {
            @Override public StreamingChatModel createClient(AiProvider p, String m, Double t, Integer max,
                    Double topP, String key, String url) { return streamingModel; }
            @Override public StreamingChatModel createStreamingClient(AiProvider p, String m, Double t,
                    Integer max, Double topP, String key, String url) { return streamingModel; }
        };
        new LangChain4jTurnStreamGateway(streamingFactory, StreamingITFixtures.policy(),
                new StreamingMetrics(), StreamingITFixtures.scheduler())
                .stream(request, event -> { });

        List<ChatMessage> syncMessages = syncPrompt.get().messages();
        List<ChatMessage> streamMessages = streamPrompt.get().messages();
        assertThat(syncMessages).hasSize(2);
        assertThat(streamMessages).hasSize(2);
        assertThat(((SystemMessage) syncMessages.get(0)).text()).isEqualTo(expectedSystem);
        assertThat(((SystemMessage) streamMessages.get(0)).text()).isEqualTo(expectedSystem);
        assertThat(((UserMessage) syncMessages.get(1)).singleText()).isEqualTo("question");
        assertThat(((UserMessage) streamMessages.get(1)).singleText()).isEqualTo("question");
    }
}
