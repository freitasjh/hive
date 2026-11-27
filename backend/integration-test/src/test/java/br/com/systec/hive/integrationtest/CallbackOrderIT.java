package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import br.com.systec.hive.publicapi.web.internal.v1.serializer.PublicSseSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** ORD-07 / RF-07: emissão na ordem dos callbacks; sem sort, sem buffer, sem precedência prometida. */
class CallbackOrderIT {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void when_reasoningPrecedesText_then_clientSeesReasoningFirst() throws Exception {
        List<TurnStreamEvent> events = List.of(
                new TurnStreamEvent.Reasoning("r"), new TurnStreamEvent.Text("t"),
                new TurnStreamEvent.Done(FinishReason.STOP, new TokenUsage(1, 2, 3)));
        assertThat(frames(events)).containsExactly("reasoning_content", "content", "finish", "usage");
    }

    @Test
    void when_textPrecedesReasoning_then_clientSeesTextFirst() throws Exception {
        List<TurnStreamEvent> events = List.of(
                new TurnStreamEvent.Text("t"), new TurnStreamEvent.Reasoning("r"),
                new TurnStreamEvent.Done(FinishReason.STOP, null));
        assertThat(frames(events)).containsExactly("content", "reasoning_content", "finish");
        assertThat(namedEvents(events)).containsExactly("text", "thinking", "done");
    }

    @Test
    void when_serializersAreScanned_then_noSortOrBufferIsUsed() throws Exception {
        var root = br.com.systec.hive.integrationtest.support.StreamingITFixtures.repoRoot();
        var sources = java.util.stream.Stream.concat(
                        java.nio.file.Files.walk(root.resolve("backend/publicapi/web/internal/v1/src/main")),
                        java.nio.file.Files.walk(root.resolve("backend/assistant/web/internal/v1/src/main")))
                .filter(java.nio.file.Files::isRegularFile)
                .filter(p -> p.toString().contains("/serializer/"))
                .toList();
        for (var source : sources) {
            String text = java.nio.file.Files.readString(source);
            assertThat(text).as(source.toString()).doesNotContain(".sort(", "sortBy", "buffer");
        }
    }

    private List<String> frames(List<TurnStreamEvent> events) throws Exception {
        List<String> seen = new ArrayList<>();
        var meta = new PublicSseSerializer.Meta("id", 1L, "m");
        for (TurnStreamEvent event : events) {
            if (event instanceof TurnStreamEvent.Reasoning r) {
                String frame = mapper.writeValueAsString(PublicSseSerializer.reasoning(meta, r));
                assertThat(frame).doesNotContain("\"content\":\"\"");
                seen.add("reasoning_content");
            } else if (event instanceof TurnStreamEvent.Text t) {
                seen.add("content");
                assertThat(mapper.writeValueAsString(PublicSseSerializer.text(meta, t))).contains("content");
            } else if (event instanceof TurnStreamEvent.Done d) {
                seen.add("finish");
                assertThat(mapper.writeValueAsString(PublicSseSerializer.finish(meta, d))).contains("finish_reason");
                if (PublicSseSerializer.usage(meta, d) != null) seen.add("usage");
            }
        }
        return seen;
    }

    private List<String> namedEvents(List<TurnStreamEvent> events) throws Exception {
        List<String> seen = new ArrayList<>();
        for (TurnStreamEvent event : events) {
            if (event instanceof TurnStreamEvent.Reasoning r) {
                seen.add(AssistantSseSerializer.thinking(r).name());
            } else if (event instanceof TurnStreamEvent.Text t) {
                seen.add(AssistantSseSerializer.text(t).name());
            } else if (event instanceof TurnStreamEvent.Done d) {
                seen.add(AssistantSseSerializer.done(d).name());
            }
        }
        return seen;
    }
}
