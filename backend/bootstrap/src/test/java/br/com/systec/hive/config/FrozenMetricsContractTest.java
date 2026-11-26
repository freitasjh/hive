package br.com.systec.hive.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MET-14/18/19: nomes congelados, grafia antiga retirada e nenhuma série semconv duplicada. */
class FrozenMetricsContractTest {

    private static final Map<String, String> FROZEN_PAIRS = Map.of(
            "chat.turns", "chat_turns_total",
            "chat.provider.calls", "chat_provider_calls_total",
            "chat.provider.failures", "chat_provider_failures_total",
            "chat.provider.latency", "chat_provider_latency_seconds",
            "chat.telemetry.export.failures", "chat_telemetry_export_failures_total",
            "chat.telemetry.redaction.failures", "chat_telemetry_redaction_failures_total",
            "chat.provider.tokens", "chat_provider_tokens_sum");

    @Test
    void when_seriesAreCharacterized_then_exactlySevenFrozenPairsExist() {
        assertThat(FROZEN_PAIRS).hasSize(7);
        assertThat(ChatMetricsRecorder.FROZEN_LOGICAL_NAMES).containsExactlyInAnyOrderElementsOf(FROZEN_PAIRS.keySet());
        assertThat(ChatMetricsRecorder.FROZEN_PROMETHEUS_NAMES)
                .containsExactlyInAnyOrderElementsOf(FROZEN_PAIRS.values());
    }

    @Test
    void when_exportFailureIsNamed_then_oldDottedSpellingIsGone() {
        assertThat(ChatMetricsRecorder.EXPORT_FAILURES).isEqualTo("chat.telemetry.export.failures");
        assertThat(LangfuseOtlpExporterConfig.FAILURE_METRIC_LOGICAL)
                .isEqualTo("chat.telemetry.export.failures");
        assertThat(LangfuseOtlpExporterConfig.FAILURE_METRIC_PROMETHEUS)
                .isEqualTo("chat_telemetry_export_failures_total");
        assertThat(ChatMetricsRecorder.FROZEN_LOGICAL_NAMES)
                .doesNotContain("chat.telemetry.export.failures.total");
    }

    @Test
    void when_seriesTypesAreChecked_then_countersEndInTotalTimerInSecondsAndSummaryInSum() {
        assertThat(FROZEN_PAIRS.entrySet()).allSatisfy(entry -> {
            if (entry.getKey().equals("chat.provider.latency")) {
                assertThat(entry.getValue()).endsWith("_seconds");
            } else if (entry.getKey().equals("chat.provider.tokens")) {
                assertThat(entry.getValue()).endsWith("_sum");
            } else {
                assertThat(entry.getValue()).endsWith("_total");
            }
        });
    }

    @Test
    void when_prometheusScrapeIsRendered_then_allSevenFrozenFamiliesAppear() {
        var registry = new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        new ChatMetricsRecorder(registry);
        String scrape = registry.scrape();

        assertThat(scrape).contains("chat_turns_total", "chat_provider_calls_total",
                "chat_provider_failures_total", "chat_provider_latency_seconds",
                "chat_telemetry_export_failures_total", "chat_telemetry_redaction_failures_total",
                "chat_provider_tokens_sum");
        assertThat(scrape).doesNotContain("gen_ai_client_token_usage", "chat_telemetry_export_failures_total_total");
    }
}
