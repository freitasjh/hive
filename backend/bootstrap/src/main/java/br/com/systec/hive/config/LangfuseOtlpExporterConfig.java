package br.com.systec.hive.config;

import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * ADR-002: liga a porta {@link ChatSpanSink} do ADR-001 a um exporter OTLP/HTTP de traces somente.
 *
 * <p>Sem registro global de OpenTelemetry: o {@code SdkTracerProvider} é dedicado, alimentado só
 * pelos spans {@code chat.turn}/{@code chat.provider.call}. O default é opt-in — sem flag, o sink é
 * noop e nenhuma credencial é exigida.
 */
@Configuration
public class LangfuseOtlpExporterConfig {

    public static final String OTLP_TRACES_PATH = "/api/public/otel/v1/traces";
    public static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";
    public static final String INGESTION_VERSION_HEADER = "x-langfuse-ingestion-version";
    public static final String INGESTION_VERSION = "4";
    public static final String AUTH_SCHEME = "Basic";
    public static final String ENABLE_ENV = "HIVE_TELEMETRY_ENABLED";
    public static final String PK_ENV = "LANGFUSE_INGESTION_PK";
    public static final String SK_ENV = "LANGFUSE_INGESTION_SK";
    public static final String ENDPOINT_ENV = "LANGFUSE_OTLP_TRACES_ENDPOINT";
    public static final String FAILURE_METRIC_LOGICAL = "chat.telemetry.export.failures";
    public static final String FAILURE_METRIC_PROMETHEUS = "chat_telemetry_export_failures_total";
    public static final boolean DEFAULT_ENABLED = false;

    static final Duration EXPORT_TIMEOUT = Duration.ofSeconds(2);

    private static final Logger log = LoggerFactory.getLogger(LangfuseOtlpExporterConfig.class);

    @Value("${HIVE_TELEMETRY_ENABLED:false}")
    private boolean enabled;

    @Value("${LANGFUSE_OTLP_TRACES_ENDPOINT:}")
    private String endpoint;

    @Value("${LANGFUSE_INGESTION_PK:}")
    private String pk;

    @Value("${LANGFUSE_INGESTION_SK:}")
    private String sk;

    @Bean
    ChatSpanSink langfuseChatSpanSink(MeterRegistry meterRegistry) {
        return create(meterRegistry, enabled, endpoint, pk, sk);
    }

    /** Fábrica estática para o IT: nenhuma credencial é literal, os valores vêm de secret/env. */
    public static ChatSpanSink create(MeterRegistry registry, boolean enabled, String endpoint, String pk, String sk) {
        if (!enabled) {
            return ChatSpanSink.noop();
        }
        if (!isValidEndpoint(endpoint) || pk == null || pk.isBlank() || sk == null || sk.isBlank()) {
            // Configuração incompleta é falha de export, nunca erro de chat (RF9).
            Counter.builder(FAILURE_METRIC_LOGICAL)
                    .description("Falhas de export OTLP de telemetria de chat")
                    .register(registry)
                    .increment();
            log.warn("chat telemetry export disabled: invalid endpoint or missing credentials from secret");
            return ChatSpanSink.noop();
        }
        return new LangfuseOtlpSpanSink(registry, endpoint, basicHeader(pk, sk));
    }

    public static String basicHeader(String pk, String sk) {
        String token = Base64.getEncoder().encodeToString((pk + ":" + sk).getBytes(StandardCharsets.UTF_8));
        return AUTH_SCHEME + " " + token;
    }

    public static Sampler samplerFor(boolean enabled) {
        return enabled ? Sampler.alwaysOn() : Sampler.alwaysOff();
    }

    /** URL completa terminando no path congelado; https fora de localhost, sem query/fragmento. */
    public static boolean isValidEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint);
            if (!OTLP_TRACES_PATH.equals(uri.getPath())
                    || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getUserInfo() != null || uri.getHost() == null) {
                return false;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return true;
            }
            return "http".equalsIgnoreCase(uri.getScheme()) && isLocalhost(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }
}
