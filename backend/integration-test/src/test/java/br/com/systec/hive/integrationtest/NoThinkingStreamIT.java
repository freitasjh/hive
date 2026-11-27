package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionChunkDTO;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OMIT-06 / RF-06: sem thinking, zero frames vazios; done/[DONE] é o único sinal de conclusão. */
class NoThinkingStreamIT {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicSseSerializer.Meta meta = new PublicSseSerializer.Meta("chatcmpl-nothink", 1L, "model-x");

    @Test
    void when_onlyTextArrives_then_noReasoningFrameIsProduced() throws Exception {
        ChatCompletionChunkDTO text = PublicSseSerializer.text(meta, new TurnStreamEvent.Text("resposta"));
        String frame = mapper.writeValueAsString(text);
        assertThat(frame).doesNotContain("reasoning_content");
        assertThat(frame).doesNotContain("\"\"");

        TurnStreamEvent.Done done = new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3));
        assertThat(mapper.writeValueAsString(PublicSseSerializer.finish(meta, done))).contains("finish_reason");
        assertThat(mapper.writeValueAsString(PublicSseSerializer.usage(meta, done))).contains("total_tokens");
    }

    @Test
    void when_onlyTextArrives_then_noNamedThinkingEventIsProduced() throws Exception {
        String data = mapper.writeValueAsString(
                AssistantSseSerializer.text(new TurnStreamEvent.Text("resposta")).data());
        assertThat(data).doesNotContain("thinking").doesNotContain("\"\"");
        assertThat(AssistantSseSerializer.done(
                new TurnStreamEvent.Done(FinishReason.STOP, null)).name()).isEqualTo("done");
    }

    @Test
    void when_cancelledBeforeTerminal_then_noUsageFrameIsInvented() {
        assertThat(PublicSseSerializer.usage(meta, new TurnStreamEvent.Done(FinishReason.STOP, null))).isNull();
    }
}
