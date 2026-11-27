package br.com.systec.hive.ai.impl;

import br.com.systec.hive.ai.api.domain.TurnReply;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** NO-14 — only the four content-free counters exist; a synchronous turn touches none of them. */
class ChatMetricsExposureTest {

    @Test
    void when_noStreamingTurn_then_allCountersAreZero() {
        StreamingMetrics metrics = new StreamingMetrics();

        for (String name : List.of(StreamingMetrics.CANCEL_UNSUPPORTED, StreamingMetrics.FAILED,
                StreamingMetrics.TIMEOUT, StreamingMetrics.CLIENT_ABORT)) {
            assertThat(metrics.get(name)).as(name).isZero();
        }
    }

    @Test
    void when_failedStreamingTurn_then_onlyFailedCounterIncrementsWithNumber() {
        StreamingMetrics metrics = new StreamingMetrics();
        metrics.increment(StreamingMetrics.FAILED);

        assertThat(metrics.get(StreamingMetrics.FAILED)).isEqualTo(1);
        assertThat(metrics.get(StreamingMetrics.TIMEOUT)).isZero();
        assertThat(metrics.get(StreamingMetrics.CANCEL_UNSUPPORTED)).isZero();
    }

    @Test
    void when_inspectTurnReply_then_noNewTextFieldForThinking() {
        List<String> components = Arrays.stream(TurnReply.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();

        assertThat(components).hasSize(9).doesNotContain("thinking", "reasoning");
    }
}
