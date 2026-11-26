package br.com.systec.hive.config;

import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MET-01..07: as sete séries são registradas nos pontos congelados, com tags fechadas. */
class ChatMetricsRecorderTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ChatMetricsRecorder recorder = new ChatMetricsRecorder(registry);

    @Test
    void when_oneTurnWithTwoAttemptsIsPublished_then_turnCallsFailuresLatencyAndTokensAreRecorded() {
        recorder.publish(List.of(
                span(true, Map.of("provider", "OPENAI", "model", "gpt-4o-mini")),
                span(false, Map.of("provider", "OPENAI", "model", "gpt-4o-mini", "attempt", "1",
                        "duration_ms", "12", "usage.input", "10", "usage.output", "5", "usage.total", "15")),
                span(false, Map.of("provider", "OPENAI", "model", "gpt-4o-mini", "attempt", "2",
                        "duration_ms", "30", "level", "ERROR", "status_message", "TimeoutException:timeout"))));

        assertThat(registry.get(ChatMetricsRecorder.TURNS).counter().count()).isEqualTo(1.0);
        assertThat(registry.find(ChatMetricsRecorder.CALLS).tags("provider", "OPENAI", "model", "gpt-4o-mini",
                "attempt", "1").counter().count()).isEqualTo(1.0);
        assertThat(registry.find(ChatMetricsRecorder.CALLS).tags("attempt", "2").counter().count()).isEqualTo(1.0);
        assertThat(registry.find(ChatMetricsRecorder.FAILURES).tags("reason", "TimeoutException:timeout")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find(ChatMetricsRecorder.LATENCY).tags("provider", "OPENAI", "model", "gpt-4o-mini")
                .timer().count()).isEqualTo(2L);
        assertThat(registry.find(ChatMetricsRecorder.TOKENS).tags("provider", "OPENAI", "model", "gpt-4o-mini",
                "token_type", "input").summary().totalAmount()).isEqualTo(10.0);
        assertThat(registry.find(ChatMetricsRecorder.TOKENS).tags("provider", "OPENAI", "model", "gpt-4o-mini",
                "token_type", "output").summary().totalAmount()).isEqualTo(5.0);
        assertThat(registry.find(ChatMetricsRecorder.TOKENS).tags("provider", "OPENAI", "model", "gpt-4o-mini",
                "token_type", "total").summary().totalAmount()).isEqualTo(15.0);
    }

    @Test
    void when_failureHasNoClassifiedReason_then_labelKeepsClassifierFormat() {
        recorder.publish(List.of(span(false, Map.of("provider", "OPENAI", "model", "gpt-4o-mini",
                "attempt", "1", "level", "ERROR"))));

        assertThat(registry.find(ChatMetricsRecorder.FAILURES)
                .tags("provider", "OPENAI", "model", "gpt-4o-mini", "reason", "unexpected:unexpected")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void when_recorderIsCreated_then_allSevenSeriesAreRegisteredEvenBeforeTraffic() {
        assertThat(registry.find(ChatMetricsRecorder.TURNS).counter()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.CALLS).counter()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.FAILURES).counter()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.LATENCY).timer()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.EXPORT_FAILURES).tag("sink", "langfuse").counter()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.REDACTION_FAILURES).counter()).isNotNull();
        assertThat(registry.find(ChatMetricsRecorder.TOKENS).summary()).isNotNull();
    }

    @Test
    void when_redactorFailsClosed_then_onlyTheRedactionCounterIncrements() {
        recorder.redactionFailure();

        assertThat(registry.get(ChatMetricsRecorder.REDACTION_FAILURES).counter().count()).isEqualTo(1.0);
        assertThat(registry.get(ChatMetricsRecorder.TURNS).counter().count()).isZero();
    }

    @Test
    void when_tokensAreRecorded_then_tokenTypeNeverGetsACacheLabel() {
        recorder.publish(List.of(span(false, Map.of("provider", "OPENAI", "model", "gpt-4o-mini",
                "attempt", "1", "usage.input", "86", "usage.output", "188",
                "usage.total", "18091", "usage_cached", "17817"))));

        assertThat(registry.find(ChatMetricsRecorder.TOKENS).summaries()).allSatisfy(summary ->
                assertThat(summary.getId().getTag("token_type")).isIn("input", "output", "total"));
    }

    private static ChatSpan span(boolean root, Map<String, String> attributes) {
        Map<String, String> copy = new LinkedHashMap<>(attributes);
        return new ChatSpan(root ? "chat.turn" : "chat.provider.call", root ? "span" : "generation",
                "trace", "span-" + attributes.hashCode(), root ? null : "root", root,
                Map.copyOf(copy), 1_000_000L, 1_012_000L);
    }
}
