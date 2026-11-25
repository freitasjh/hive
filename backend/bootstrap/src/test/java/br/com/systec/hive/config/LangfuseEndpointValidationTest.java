package br.com.systec.hive.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OTL-08/NFR-01: TLS fora de localhost, path congelado, sem query/fragmento. */
class LangfuseEndpointValidationTest {

    private static final String PATH = "/api/public/otel/v1/traces";

    @Test
    void when_endpointIsHttpsWithFrozenPath_then_accepted() {
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("https://langfuse.example.com" + PATH)).isTrue();
    }

    @Test
    void when_endpointIsHttpOnRemoteHost_then_rejected() {
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("http://langfuse.example.com" + PATH)).isFalse();
    }

    @Test
    void when_endpointIsHttpOnLocalhost_then_accepted() {
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("http://localhost:4318" + PATH)).isTrue();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("http://127.0.0.1:4318" + PATH)).isTrue();
    }

    @Test
    void when_endpointHasWrongPathQueryFragmentOrNoScheme_then_rejected() {
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("https://langfuse.example.com/api/public/ingestion")).isFalse();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("https://langfuse.example.com" + PATH + "?x=1")).isFalse();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("https://langfuse.example.com" + PATH + "#frag")).isFalse();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("langfuse.example.com" + PATH)).isFalse();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("")).isFalse();
    }

    @Test
    void when_endpointCarriesUserInfoOrHasNoHost_then_rejected() {
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint(
                "https://user:password@langfuse.example.com" + PATH)).isFalse();
        assertThat(LangfuseOtlpExporterConfig.isValidEndpoint("https:///" + PATH)).isFalse();
    }
}
