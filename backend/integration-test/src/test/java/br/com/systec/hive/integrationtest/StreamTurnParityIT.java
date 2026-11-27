package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** OBS-13 / NFR-05: mesmo TurnStreamEvent nos dois endpoints; só o serializador muda. */
class StreamTurnParityIT {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void when_sameEventsAreSerialized_then_channelsAndTerminalsCorrespondOneToOne() throws Exception {
        List<TurnStreamEvent> events = List.of(
                new TurnStreamEvent.Reasoning("r"), new TurnStreamEvent.Text("t"),
                new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3)));
        var meta = new PublicSseSerializer.Meta("id", 1L, "m");

        StringBuilder publicWire = new StringBuilder();
        StringBuilder assistantWire = new StringBuilder();
        for (TurnStreamEvent event : events) {
            if (event instanceof TurnStreamEvent.Reasoning r) {
                publicWire.append(mapper.writeValueAsString(PublicSseSerializer.reasoning(meta, r)));
                assistantWire.append(AssistantSseSerializer.thinking(r).name());
            } else if (event instanceof TurnStreamEvent.Text t) {
                publicWire.append(mapper.writeValueAsString(PublicSseSerializer.text(meta, t)));
                assistantWire.append(AssistantSseSerializer.text(t).name());
            } else if (event instanceof TurnStreamEvent.Done d) {
                publicWire.append(mapper.writeValueAsString(PublicSseSerializer.finish(meta, d)));
                publicWire.append(mapper.writeValueAsString(PublicSseSerializer.usage(meta, d)));
                assistantWire.append(AssistantSseSerializer.done(d).name());
            }
        }
        assertThat(publicWire.toString()).contains("reasoning_content", "\"content\":\"t\"", "finish_reason", "total_tokens");
        assertThat(assistantWire.toString()).isEqualTo("thinkingtextdone");
    }

    @Test
    void when_turnReplyIsInspected_then_itStillCarriesNoText() {
        TurnReply reply = TurnReply.answered("answer", 1, 5, 1, 2, 3, "STOP");
        assertThat(reply.toString()).doesNotContain("answer");
    }

    @Test
    void when_errorIsSerializedByBoth_then_publicUsesOpenAiErrorAndAssistantUsesNamedEvent() throws Exception {
        TurnStreamEvent.Failed failed = new TurnStreamEvent.Failed("timeout", new RuntimeException("raw"));
        assertThat(mapper.writeValueAsString(PublicSseSerializer.error(failed))).contains("server_error");
        assertThat(AssistantSseSerializer.error(failed).name()).isEqualTo("error");
        assertThat(AssistantSseSerializer.done(
                new TurnStreamEvent.Done(FinishReason.STOP, null)).name()).isEqualTo("done");
    }
}
