package br.com.systec.hive.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** OTL-14: caracterização da Interface congelada do ADR-002, com os ajustes de 2026-11-25. */
class FrozenIngestionContractTest {

    @Test
    void when_frozenNamesAreRead_then_theyMatchAdr002() {
        assertThat(LangfuseOtlpExporterConfig.OTLP_TRACES_PATH).isEqualTo("/api/public/otel/v1/traces");
        assertThat(LangfuseOtlpExporterConfig.CONTENT_TYPE_PROTOBUF).isEqualTo("application/x-protobuf");
        assertThat(LangfuseOtlpExporterConfig.INGESTION_VERSION_HEADER).isEqualTo("x-langfuse-ingestion-version");
        assertThat(LangfuseOtlpExporterConfig.INGESTION_VERSION).isEqualTo("4");
        assertThat(LangfuseOtlpExporterConfig.AUTH_SCHEME).isEqualTo("Basic");
        assertThat(LangfuseOtlpExporterConfig.ENABLE_ENV).isEqualTo("HIVE_TELEMETRY_ENABLED");
        assertThat(LangfuseOtlpExporterConfig.PK_ENV).isEqualTo("LANGFUSE_INGESTION_PK");
        assertThat(LangfuseOtlpExporterConfig.SK_ENV).isEqualTo("LANGFUSE_INGESTION_SK");
        assertThat(LangfuseOtlpExporterConfig.ENDPOINT_ENV).isEqualTo("LANGFUSE_OTLP_TRACES_ENDPOINT");
        assertThat(LangfuseOtlpExporterConfig.FAILURE_METRIC_LOGICAL).isEqualTo("chat.telemetry.export.failures");
        assertThat(LangfuseOtlpExporterConfig.FAILURE_METRIC_PROMETHEUS).isEqualTo("chat_telemetry_export_failures_total");
        assertThat(LangfuseOtlpExporterConfig.DEFAULT_ENABLED).isFalse();
    }

    @Test
    void when_configurationFilesAreScanned_then_noLegacyEncodingOldMetricNameOrTenPercentSampler() throws IOException {
        String properties = resource("/application.properties")
                + resource("/application-dev.properties")
                + resource("/application-prod.properties");

        assertThat(properties).doesNotContain("http/json", "application/json",
                "parent-based-trace-id-ratio", "chat.telemetry.export.failures.total");
        assertThat(properties).doesNotContain("HIVE_TELEMETRY_ENABLED=");
    }

    private String resource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(name)) {
            assertThat(stream).as("recurso %s", name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
