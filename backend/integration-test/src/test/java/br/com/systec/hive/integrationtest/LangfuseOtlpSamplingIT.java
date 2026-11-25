package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
import br.com.systec.hive.config.LangfuseOtlpSpanSink;
import br.com.systec.hive.integrationtest.support.OtlpCaptureServer;
import br.com.systec.hive.integrationtest.support.OtlpPayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OTL-06: default ausente/false = always-off; true = always-on (100%). Sem sampler 10%. */
class LangfuseOtlpSamplingIT {

    @Test
    void when_flagIsAbsentOrFalse_then_noSpanIsPublishedAndNoRequestIsSent() throws Exception {
        assertThat(LangfuseOtlpExporterConfig.DEFAULT_ENABLED).isFalse();
        assertThat(LangfuseOtlpExporterConfig.samplerFor(false).getDescription()).isEqualTo("AlwaysOffSampler");
        assertThat(LangfuseOtlpExporterConfig.samplerFor(false).getDescription()).doesNotContain("ratio");

        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { })) {
            for (boolean enabled : List.of(false, LangfuseOtlpExporterConfig.DEFAULT_ENABLED)) {
                ChatSpanSink sink = LangfuseOtlpExporterConfig.create(new SimpleMeterRegistry(), enabled,
                        server.endpoint(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
                sink.publish(List.of(rootSpan()));
                if (sink instanceof LangfuseOtlpSpanSink active) {
                    active.flush();
                    active.close();
                }
            }
            assertThat(server.requests()).isEmpty();
        }
    }

    @Test
    void when_flagIsTrueWithBlankSecret_then_failureCounterIncrementsAndNoRequestIsSent() throws Exception {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { })) {
            ChatSpanSink sink = LangfuseOtlpExporterConfig.create(registry, true, server.endpoint(),
                    UUID.randomUUID().toString(), "");
            sink.publish(List.of(rootSpan()));

            assertThat(sink).isNotInstanceOf(LangfuseOtlpSpanSink.class);
            assertThat(server.requests()).isEmpty();
            assertThat(registry.scrape()).contains("chat_telemetry_export_failures_total 1.0");
        }
    }

    @Test
    void when_flagIsTrue_then_everyTurnProducesOneBatchWithRoot() throws Exception {
        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink sink = (LangfuseOtlpSpanSink) LangfuseOtlpExporterConfig.create(
                     new SimpleMeterRegistry(), true, server.endpoint(),
                     UUID.randomUUID().toString(), UUID.randomUUID().toString())) {
            assertThat(LangfuseOtlpExporterConfig.samplerFor(true).getDescription()).isEqualTo("AlwaysOnSampler");

            for (int turn = 0; turn < 3; turn++) {
                sink.publish(List.of(rootSpan()));
                sink.flush();
            }

            assertThat(server.requests()).hasSize(3);
            assertThat(server.requests()).allSatisfy(request -> {
                assertThat(request.header("x-langfuse-ingestion-version")).isEqualTo("4");
                var payload = OtlpPayload.decodeOrNull(request.body());
                assertThat(payload).isNotNull();
                var root = OtlpPayload.spans(payload).stream()
                        .filter(span -> span.getName().equals("chat.turn")).findFirst().orElseThrow();
                assertThat(root.getParentSpanId().isEmpty()).isTrue();
            });
        }
    }

    private static ChatSpan rootSpan() {
        Instant now = Instant.now();
        long epochNano = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        return new ChatSpan("chat.turn", "span",
                UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                UUID.randomUUID().toString().replace("-", "").substring(0, 16), null, true,
                Map.of("langfuse.observation.type", "span"), epochNano, epochNano + 1_000_000L);
    }
}
