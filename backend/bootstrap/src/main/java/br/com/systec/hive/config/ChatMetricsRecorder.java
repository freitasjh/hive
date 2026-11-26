package br.com.systec.hive.config;

import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ADR-005: as sete séries operacionais no Micrometer, registradas nos pontos já instrumentados
 * (turno, callbacks do provider, redator e exporter). Nenhum OTLP metrics é registrado — o scrape
 * local é o único destino destas séries.
 */
public final class ChatMetricsRecorder implements ChatSpanSink {

    public static final String TURNS = "chat.turns";
    public static final String CALLS = "chat.provider.calls";
    public static final String FAILURES = "chat.provider.failures";
    public static final String LATENCY = "chat.provider.latency";
    public static final String EXPORT_FAILURES = "chat.telemetry.export.failures";
    public static final String REDACTION_FAILURES = "chat.telemetry.redaction.failures";
    public static final String TOKENS = "chat.provider.tokens";

    public static final Set<String> FROZEN_LOGICAL_NAMES = Set.of(
            TURNS, CALLS, FAILURES, LATENCY, EXPORT_FAILURES, REDACTION_FAILURES, TOKENS);
    public static final Set<String> FROZEN_PROMETHEUS_NAMES = Set.of(
            "chat_turns_total", "chat_provider_calls_total", "chat_provider_failures_total",
            "chat_provider_latency_seconds", "chat_telemetry_export_failures_total",
            "chat_telemetry_redaction_failures_total", "chat_provider_tokens_sum");

    private static final String PROVIDER = "provider";
    private static final String MODEL = "model";
    private static final String ATTEMPT = "attempt";
    private static final String REASON = "reason";
    private static final String TOKEN_TYPE = "token_type";

    private final MeterRegistry registry;
    private final Counter turns;
    private final Counter redactionFailures;

    public ChatMetricsRecorder(MeterRegistry registry) {
        this.registry = registry;
        this.turns = Counter.builder(TURNS).description("Volume de turnos de chat").register(registry);
        Counter.builder(EXPORT_FAILURES).tag("sink", "langfuse")
                .description("Falhas de export OTLP de telemetria de chat").register(registry);
        this.redactionFailures = Counter.builder(REDACTION_FAILURES)
                .description("Fail-closed do redator de texto").register(registry);

        // Registros base com valores neutros mantêm as famílias no scrape antes de qualquer tráfego.
        // Prometheus exige o mesmo conjunto de rótulos por família; daí o placeholder com contagem zero.
        Counter.builder(CALLS).tags(PROVIDER, "none", MODEL, "none", ATTEMPT, "1")
                .description("Chamadas ao provider, incluindo retries").register(registry);
        Counter.builder(FAILURES).tags(PROVIDER, "none", MODEL, "none", REASON, "unexpected:unexpected")
                .description("Falhas do provider com motivo classificado").register(registry);
        Timer.builder(LATENCY).tags(PROVIDER, "none", MODEL, "none")
                .description("Latência da chamada ao provider").register(registry);
        for (String type : List.of("input", "output", "total")) {
            DistributionSummary.builder(TOKENS).tags(PROVIDER, "none", MODEL, "none", TOKEN_TYPE, type)
                    .description("Tokens reportados pelo provider").register(registry);
        }
    }

    /** Compõe o recorder com o destino OTLP sem criar endpoint próprio nem registro global. */
    public ChatSpanSink decorating(ChatSpanSink delegate) {
        return spans -> {
            publish(spans);
            delegate.publish(spans);
        };
    }

    @Override
    public void publish(List<ChatSpan> spans) {
        for (ChatSpan span : spans) {
            if (span.rootObservation()) {
                turns.increment();
                continue;
            }
            Map<String, String> attributes = span.attributes();
            String provider = attributes.getOrDefault(PROVIDER, "none");
            String model = attributes.getOrDefault(MODEL, "none");
            String attempt = attributes.getOrDefault(ATTEMPT, "1");

            registry.counter(CALLS, tags(PROVIDER, provider, MODEL, model, ATTEMPT, attempt)).increment();
            if ("ERROR".equals(attributes.get("level"))) {
                String reason = attributes.getOrDefault("status_message", "unexpected:unexpected");
                registry.counter(FAILURES, tags(PROVIDER, provider, MODEL, model, REASON, reason)).increment();
            }
            long durationMs = parseLong(attributes.get("duration_ms"), 0L);
            if (durationMs > 0L) {
                registry.timer(LATENCY, tags(PROVIDER, provider, MODEL, model))
                        .record(durationMs, TimeUnit.MILLISECONDS);
            }
            recordTokens(attributes, provider, model);
        }
    }

    /** Incrementado pelo callback do redator do ADR-003; nenhum texto entra na série. */
    public void redactionFailure() {
        redactionFailures.increment();
    }

    /** Contagem local espelhada, usada para provar que o callback foi chamado uma vez por evento. */
    public double redactionCount() {
        return registry.get(REDACTION_FAILURES).counter().count();
    }

    private void recordTokens(Map<String, String> attributes, String provider, String model) {
        recordToken(attributes, "usage.input", "input", provider, model);
        recordToken(attributes, "usage.output", "output", provider, model);
        recordToken(attributes, "usage.total", "total", provider, model);
    }

    private void recordToken(Map<String, String> attributes, String key, String type,
                             String provider, String model) {
        long amount = parseLong(attributes.get(key), -1L);
        if (amount >= 0L) {
            registry.summary(TOKENS, tags(PROVIDER, provider, MODEL, model, TOKEN_TYPE, type)).record(amount);
        }
    }

    private static Iterable<Tag> tags(String... pairs) {
        return io.micrometer.core.instrument.Tags.of(pairs);
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
