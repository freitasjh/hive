package br.com.systec.hive.config;

import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Exporter isolado e best-effort: {@code SdkTracerProvider} próprio, nunca o global, sem
 * auto-instrumentação, sem OTLP metrics/logs. Falha de export vira contador + log classificado.
 */
public final class LangfuseOtlpSpanSink implements ChatSpanSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LangfuseOtlpSpanSink.class);
    private static final Duration BATCH_DELAY = Duration.ofMillis(500);

    private final SdkTracerProvider provider;
    private final Tracer tracer;
    private final Counter failures;

    LangfuseOtlpSpanSink(MeterRegistry registry, String endpoint, String authorization) {
        this.failures = Counter.builder(LangfuseOtlpExporterConfig.FAILURE_METRIC_LOGICAL)
                .description("Falhas de export OTLP de telemetria de chat")
                .register(registry);
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint)
                .addHeader("Authorization", authorization)
                .addHeader(LangfuseOtlpExporterConfig.INGESTION_VERSION_HEADER,
                        LangfuseOtlpExporterConfig.INGESTION_VERSION)
                .setTimeout(LangfuseOtlpExporterConfig.EXPORT_TIMEOUT)
                .setConnectTimeout(LangfuseOtlpExporterConfig.EXPORT_TIMEOUT)
                .build();
        this.provider = SdkTracerProvider.builder()
                .setSampler(LangfuseOtlpExporterConfig.samplerFor(true))
                .addSpanProcessor(BatchSpanProcessor.builder(new CountingExporter(exporter))
                        .setScheduleDelay(BATCH_DELAY)
                        .setExporterTimeout(LangfuseOtlpExporterConfig.EXPORT_TIMEOUT)
                        .build())
                .build();
        this.tracer = provider.get("hive.telemetry.langfuse");
    }

    @Override
    public void publish(List<ChatSpan> spans) {
        try {
            Map<String, Span> bySourceId = new HashMap<>();
            for (ChatSpan span : spans) {
                SpanBuilder builder = tracer.spanBuilder(span.name());
                Span parent = span.rootObservation() || span.parentSpanId() == null
                        ? null : bySourceId.get(span.parentSpanId());
                if (parent == null) {
                    builder.setNoParent();
                } else {
                    builder.setParent(Context.root().with(Span.wrap(parent.getSpanContext())));
                }
                span.attributes().forEach(builder::setAttribute);
                builder.setStartTimestamp(toInstant(span.startTimeUnixNano()));
                Span created = builder.startSpan();
                bySourceId.put(span.spanId(), created);
                created.end(toInstant(span.endTimeUnixNano()));
            }
        } catch (RuntimeException ignored) {
            // Best-effort: publicar spans nunca altera o desfecho do turno.
            failure();
        }
    }

    /** Usado pelos ITs para tornar determinística a captura do lote assíncrono. */
    public void flush() {
        provider.forceFlush().join(10, TimeUnit.SECONDS);
    }

    @PreDestroy
    @Override
    public void close() {
        provider.close();
    }

    private void failure() {
        failures.increment();
        // Sem getMessage(), sem payload, sem credencial — apenas classe do problema.
        log.warn("chat telemetry export failed; classification=transport");
    }

    private static Instant toInstant(long epochNano) {
        return Instant.ofEpochSecond(epochNano / 1_000_000_000L, epochNano % 1_000_000_000L);
    }

    private final class CountingExporter implements SpanExporter {

        private final SpanExporter delegate;

        private CountingExporter(SpanExporter delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            try {
                CompletableResultCode result = delegate.export(spans);
                // export() devolve PENDENTE: isSuccess() imediato é false mesmo em 200.
                result.whenComplete(() -> {
                    if (!result.isSuccess()) {
                        failure();
                    }
                });
                return result;
            } catch (RuntimeException e) {
                failure();
                return CompletableResultCode.ofFailure();
            }
        }

        @Override
        public CompletableResultCode flush() {
            return delegate.flush();
        }

        @Override
        public CompletableResultCode shutdown() {
            return delegate.shutdown();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
