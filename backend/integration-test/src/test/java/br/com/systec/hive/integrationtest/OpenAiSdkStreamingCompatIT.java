package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.publicapi.api.dto.OpenAiError;
import br.com.systec.hive.publicapi.api.dto.StreamOptionsDTO;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CMP-09 / AC-14: cliente OpenAI real (langchain4j embutido) consumindo a wire exata produzida
 * pelo serializador público, servida por um stub HTTP local — sem dependência nova no pom.
 */
class OpenAiSdkStreamingCompatIT {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void when_realOpenAiClientConsumesOurWire_thenThinkingTextFinishAndDoneAreParsed() throws Exception {
        var meta = new PublicSseSerializer.Meta("chatcmpl-sdk", 1L, "model-x");
        TurnStreamEvent.Done done = new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3));
        StringBuilder wire = new StringBuilder();
        frame(wire, mapper.writeValueAsString(PublicSseSerializer.reasoning(meta,
                new TurnStreamEvent.Reasoning("raciocinio"))));
        frame(wire, mapper.writeValueAsString(PublicSseSerializer.text(meta,
                new TurnStreamEvent.Text("resposta"))));
        frame(wire, mapper.writeValueAsString(PublicSseSerializer.finish(meta, done)));
        frame(wire, mapper.writeValueAsString(PublicSseSerializer.usage(meta, done)));
        wire.append("data: [DONE]\n\n");

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = wire.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .apiKey("test-key").modelName("model-x").returnThinking(true).build();
            List<String> thinking = new CopyOnWriteArrayList<>();
            List<String> text = new CopyOnWriteArrayList<>();
            List<Throwable> errors = new CopyOnWriteArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            ChatResponse[] response = new ChatResponse[1];

            model.chat("pergunta", new StreamingChatResponseHandler() {
                @Override public void onPartialThinking(PartialThinking partial) {
                    thinking.add(partial.text());
                }
                @Override public void onPartialResponse(String partial) {
                    text.add(partial);
                }
                @Override public void onCompleteResponse(ChatResponse complete) {
                    response[0] = complete;
                    completed.countDown();
                }
                @Override public void onError(Throwable error) {
                    errors.add(error);
                    completed.countDown();
                }
            });

            assertThat(completed.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(errors).isEmpty();
            assertThat(thinking).containsExactly("raciocinio");
            assertThat(text).containsExactly("resposta");
            assertThat(response[0]).isNotNull();
            assertThat(response[0].finishReason()).isEqualTo(FinishReason.STOP);
            assertThat(response[0].tokenUsage().totalTokenCount()).isEqualTo(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void when_streamOptionsHasFutureFields_then_deserializationIgnoresThem() throws Exception {
        StreamOptionsDTO options = mapper.readValue(
                "{\"include_usage\":true,\"future_flag\":1,\"ignoreUnknown\":true}", StreamOptionsDTO.class);
        assertThat(options.getIncludeUsage()).isTrue();
        assertThat(options.isUnknown()).isFalse();
    }

    @Test
    void when_errorFrameIsBuilt_then_shapeMatchesTheFrozenOpenAiError() throws Exception {
        OpenAiError error = PublicSseSerializer.error(
                new TurnStreamEvent.Failed("stream_failed", new RuntimeException("raw")));
        var node = mapper.readTree(mapper.writeValueAsString(error)).path("error");
        assertThat(node.path("message").asText()).isEqualTo("stream_failed");
        assertThat(node.path("type").asText()).isEqualTo("server_error");
        assertThat(node.path("param").isNull()).isTrue();
        assertThat(node.path("code").asText()).isEqualTo("stream_failed");
    }

    private static void frame(StringBuilder wire, String json) {
        wire.append("data: ").append(json).append("\n\n");
    }
}
