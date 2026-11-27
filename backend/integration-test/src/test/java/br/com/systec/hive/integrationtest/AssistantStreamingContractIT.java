package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AST-04 / RF-04: eventos nomeados thinking/text/done/error do mesmo TurnStreamEvent. */
class AssistantStreamingContractIT {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void when_thinkingAndText_then_namedEventsCarryMatchingType() throws Exception {
        AssistantSseSerializer.NamedEvent thinking = AssistantSseSerializer.thinking(
                new TurnStreamEvent.Reasoning("raciocinio"));
        assertThat(thinking.name()).isEqualTo("thinking");
        JsonNode data = json(thinking.data());
        assertThat(data.path("type").asText()).isEqualTo("thinking");
        assertThat(data.path("text").asText()).isEqualTo("raciocinio");

        AssistantSseSerializer.NamedEvent text = AssistantSseSerializer.text(
                new TurnStreamEvent.Text("resposta"));
        assertThat(text.name()).isEqualTo("text");
        assertThat(json(text.data()).path("type").asText()).isEqualTo("text");
    }

    @Test
    void when_done_then_namedEventHasFinishReasonAndUsage() throws Exception {
        AssistantSseSerializer.NamedEvent done = AssistantSseSerializer.done(
                new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(12, 34, 46)));
        assertThat(done.name()).isEqualTo("done");
        JsonNode data = json(done.data());
        assertThat(data.path("type").asText()).isEqualTo("done");
        assertThat(data.path("finish_reason").asText()).isEqualTo("stop");
        assertThat(data.path("usage").path("total_tokens").asInt()).isEqualTo(46);
    }

    @Test
    void when_doneWithoutUsage_then_usageFieldIsOmitted() throws Exception {
        JsonNode data = json(AssistantSseSerializer.done(
                new TurnStreamEvent.Done(FinishReason.STOP, null)).data());
        assertThat(data.has("usage")).isFalse();
    }

    @Test
    void when_failed_then_errorEventCarriesOnlyTheClosedCode() throws Exception {
        AssistantSseSerializer.NamedEvent error = AssistantSseSerializer.error(
                new TurnStreamEvent.Failed("timeout", new IllegalStateException("SEGREDO-NAO-VAZA")));
        assertThat(error.name()).isEqualTo("error");
        JsonNode data = json(error.data());
        assertThat(data.path("type").asText()).isEqualTo("error");
        assertThat(data.path("error").path("code").asText()).isEqualTo("timeout");
        assertThat(data.path("error").path("message").asText()).isEqualTo("timeout");
        assertThat(data.toString()).doesNotContain("SEGREDO-NAO-VAZA");
    }

    private JsonNode json(Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }
}
