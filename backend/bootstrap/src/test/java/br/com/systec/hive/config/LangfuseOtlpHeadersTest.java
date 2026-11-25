package br.com.systec.hive.config;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OTL-02/OTL-03: Basic pk:sk e header v4, sem literal de credencial no teste. */
class LangfuseOtlpHeadersTest {

    @Test
    void when_credentialsAreSupplied_then_basicHeaderDecodesToPkColonSk() {
        String pk = UUID.randomUUID().toString();
        String sk = UUID.randomUUID().toString();

        String header = LangfuseOtlpExporterConfig.basicHeader(pk, sk);

        assertThat(header).startsWith("Basic ");
        assertThat(new String(Base64.getDecoder().decode(header.substring("Basic ".length())), StandardCharsets.UTF_8))
                .isEqualTo(pk + ":" + sk);
    }

    @Test
    void when_enabledWithBlankSecret_then_failureCounterIncrementsAndSinkIsNoop() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        var sink = LangfuseOtlpExporterConfig.create(registry, true,
                "https://langfuse.example.com/api/public/otel/v1/traces",
                UUID.randomUUID().toString(), "");

        assertThat(sink).isNotInstanceOf(LangfuseOtlpSpanSink.class);
        assertThat(registry.scrape()).contains("chat_telemetry_export_failures_total 1.0");
    }

    @Test
    void when_samplerIsChosen_then_flagMapsOnlyToAlwaysOnOrAlwaysOff() {
        assertThat(LangfuseOtlpExporterConfig.samplerFor(true).getDescription()).isEqualTo("AlwaysOnSampler");
        assertThat(LangfuseOtlpExporterConfig.samplerFor(false).getDescription()).isEqualTo("AlwaysOffSampler");
        assertThat(LangfuseOtlpExporterConfig.samplerFor(true).getDescription()).doesNotContain("ratio");
        assertThat(LangfuseOtlpExporterConfig.samplerFor(false).getDescription()).doesNotContain("ratio");
    }
}
