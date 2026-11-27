package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionChunkDTO;
import br.com.systec.hive.publicapi.api.dto.OpenAiError;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** PUB-03 / RF-03: wire data-only, canais separados, choices:[] + usage e [DONE] literal. */
class PublicStreamingContractIT {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicSseSerializer.Meta meta = new PublicSseSerializer.Meta("chatcmpl-abc12345", 1764153600L, "model-x");

    @Test
    void when_reasoning_then_chunkHasOnlyReasoningContent() throws Exception {
        JsonNode chunk = json(PublicSseSerializer.reasoning(meta, new TurnStreamEvent.Reasoning("pensei")));
        assertThat(chunk.path("object").asText()).isEqualTo("chat.completion.chunk");
        assertThat(chunk.path("id").asText()).isEqualTo("chatcmpl-abc12345");
        assertThat(chunk.path("created").asLong()).isEqualTo(1764153600L);
        assertThat(chunk.path("model").asText()).isEqualTo("model-x");
        assertThat(chunk.path("choices").get(0).path("delta").path("reasoning_content").asText()).isEqualTo("pensei");
        assertThat(chunk.path("choices").get(0).path("delta").has("content")).isFalse();
        assertThat(chunk.path("choices").get(0).path("finish_reason").isNull()).isTrue();
        assertThat(chunk.has("usage")).isTrue();
        assertThat(chunk.path("usage").isNull()).isTrue();
    }

    @Test
    void when_text_then_chunkHasOnlyContent() throws Exception {
        JsonNode chunk = json(PublicSseSerializer.text(meta, new TurnStreamEvent.Text("resposta")));
        assertThat(chunk.path("choices").get(0).path("delta").path("content").asText()).isEqualTo("resposta");
        assertThat(chunk.path("choices").get(0).path("delta").has("reasoning_content")).isFalse();
    }

    @Test
    void when_done_then_finishChunkHasEmptyDeltaAndTerminalChunkHasEmptyChoices() throws Exception {
        TurnStreamEvent.Done done = new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(12, 34, 46));
        JsonNode finish = json(PublicSseSerializer.finish(meta, done));
        assertThat(finish.path("choices").get(0).path("delta")).isEmpty();
        assertThat(finish.path("choices").get(0).path("finish_reason").asText()).isEqualTo("stop");

        JsonNode terminal = json(PublicSseSerializer.usage(meta, done));
        assertThat(terminal.path("choices")).isEmpty();
        assertThat(terminal.path("usage").path("prompt_tokens").asInt()).isEqualTo(12);
        assertThat(terminal.path("usage").path("completion_tokens").asInt()).isEqualTo(34);
        assertThat(terminal.path("usage").path("total_tokens").asInt()).isEqualTo(46);
        assertThat(terminal.toString()).doesNotContain("reasoning_tokens", "completion_tokens_details");
    }

    @Test
    void when_doneWithoutUsage_then_noTerminalUsageChunkIsProduced() {
        TurnStreamEvent.Done done = new TurnStreamEvent.Done(FinishReason.STOP, null);
        assertThat(PublicSseSerializer.usage(meta, done)).isNull();
        assertThat(PublicSseSerializer.finish(meta, done)).isNotNull();
    }

    @Test
    void when_failed_then_errorFrameUsesClosedCodeAsMessage() throws Exception {
        OpenAiError error = PublicSseSerializer.error(
                new TurnStreamEvent.Failed("stream_failed", new IllegalStateException("segredo")));
        JsonNode node = json(error);
        assertThat(node.path("error").path("code").asText()).isEqualTo("stream_failed");
        assertThat(node.path("error").path("message").asText()).isEqualTo("stream_failed");
        assertThat(node.path("error").path("type").asText()).isEqualTo("server_error");
        assertThat(node.path("error").path("param").isNull()).isTrue();
        assertThat(node.toString()).doesNotContain("segredo");
    }

    private JsonNode json(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }
}
