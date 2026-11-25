package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Escopo de turno (thread-local) que sustenta a hierarquia ADR-001: 1 root e N filhos.
 *
 * <p>O root abre na entrada do turno, antes de qualquer chamada ao provider; cada callback do
 * {@code ChatModelListener} acrescenta uma filha ao mesmo {@code traceId}. O fechamento acontece no
 * ponto {@code ChatTraceRecorder.record(ChatTrace)}, onde a correlação completa (tenant/turn/client)
 * só então existe. Nada é publicado antes disso — um turno é uma unidade atômica de observations.
 *
 * <p>Thread-local é a mecânica registrada no ADR; um fluxo reativo exige novo ADR, não uma
 * adaptação silenciosa aqui.
 */
public final class ChatTurnSpanScope {

    private static final ThreadLocal<Turn> CURRENT = new ThreadLocal<>();

    private ChatTurnSpanScope() {
    }

    public static void open(UUID sessionId, UUID promptTemplateId, AiProvider provider, String model,
                            ChatSpanSink sink) {
        closeIfOpen(); // um turno abandonado não pode contaminar o próximo
        String traceId = newId(32);
        String rootId = newId(16);
        Map<String, String> attributes = new LinkedHashMap<>();
        SpanAttributeMapper.typeAndTrace(attributes, "span");
        attributes.put(SpanAttributeMapper.KEY_LEVEL, "DEFAULT");
        if (sessionId != null) {
            attributes.put(SpanAttributeMapper.KEY_SESSION, sessionId.toString());
        }
        if (promptTemplateId != null) {
            attributes.put(SpanAttributeMapper.KEY_PROMPT_TEMPLATE, promptTemplateId.toString());
        }
        if (provider != null) {
            attributes.put("provider", provider.name());
        }
        if (model != null) {
            attributes.put("model", model);
        }
        Turn turn = new Turn(traceId, rootId, attributes, sink == null ? ChatSpanSink.noop() : sink);
        turn.spans.add(new MutableSpan(SpanAttributeMapper.ROOT_NAME, "span", traceId, rootId, null,
                true, attributes, nowNanos()));
        CURRENT.set(turn);
    }

    /** Uma tentativa ao provider: uma filha, numerada por turno, nunca um root novo. */
    public static void attemptStarted() {
        Turn turn = CURRENT.get();
        if (turn == null) {
            return;
        }
        turn.attempt++;
        Map<String, String> attributes = new LinkedHashMap<>(turn.baseAttributes);
        SpanAttributeMapper.typeAndTrace(attributes, "generation");
        attributes.put(SpanAttributeMapper.KEY_ATTEMPT, Integer.toString(turn.attempt));
        MutableSpan child = new MutableSpan(SpanAttributeMapper.CHILD_NAME, "generation", turn.traceId,
                newId(16), turn.rootSpanId, false, attributes, nowNanos());
        turn.spans.add(child);
        turn.currentAttempt = child;
    }

    public static void attemptSucceeded(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        MutableSpan attempt = attemptToClose();
        if (attempt == null) {
            return;
        }
        putNumber(attempt.attributes, "usage.input", inputTokens);
        putNumber(attempt.attributes, "usage.output", outputTokens);
        putNumber(attempt.attributes, "usage.total", totalTokens);
        attempt.endNano = nowNanos();
        detachCurrentAttempt();
    }

    public static void attemptFailed(Throwable error) {
        MutableSpan attempt = attemptToClose();
        if (attempt == null) {
            return;
        }
        attempt.attributes.put(SpanAttributeMapper.KEY_LEVEL, "ERROR");
        String label = FailureClassifier.causeOf(error);
        if (label != null) {
            attempt.attributes.put(SpanAttributeMapper.KEY_STATUS_MESSAGE, label);
        }
        attempt.endNano = nowNanos();
        detachCurrentAttempt();
    }

    /** Chamado por `SpanningChatTraceRecorder.record`: enriquece e publica o turno inteiro. */
    public static void close(ChatTrace trace) {
        Turn turn = CURRENT.get();
        if (turn == null) {
            return;
        }
        try {
            long end = nowNanos();
            for (MutableSpan span : turn.spans) {
                if (span.endNano == 0) {
                    span.endNano = end;
                }
                SpanAttributeMapper.applyCorrelation(span.attributes, trace);
            }
            try {
                turn.sink.publish(turn.spans.stream().map(MutableSpan::toSpan).toList());
            } catch (RuntimeException ignored) {
                // best-effort: falha do exporter nunca troca o desfecho do turno nem sufoca o log.
            }
        } finally {
            CURRENT.remove();
        }
    }

    /** Rede de segurança para o raro caminho que termina sem chamar `record`. */
    public static void closeIfOpen() {
        close(null);
    }

    private static MutableSpan attemptToClose() {
        Turn turn = CURRENT.get();
        return turn == null ? null : turn.currentAttempt;
    }

    private static void detachCurrentAttempt() {
        Turn turn = CURRENT.get();
        if (turn != null) {
            turn.currentAttempt = null;
        }
    }

    private static void putNumber(Map<String, String> attributes, String key, Number value) {
        if (value != null) {
            attributes.put(key, value.toString());
        }
    }

    private static long nowNanos() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }

    private static String newId(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }

    private static final class Turn {
        private final String traceId;
        private final String rootSpanId;
        private final Map<String, String> baseAttributes;
        private final ChatSpanSink sink;
        private final List<MutableSpan> spans = new ArrayList<>();
        private int attempt;
        private MutableSpan currentAttempt;

        private Turn(String traceId, String rootSpanId, Map<String, String> baseAttributes, ChatSpanSink sink) {
            this.traceId = traceId;
            this.rootSpanId = rootSpanId;
            this.baseAttributes = baseAttributes;
            this.sink = sink;
        }
    }

    private static final class MutableSpan {
        private final String name;
        private final String type;
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final boolean rootObservation;
        private final Map<String, String> attributes;
        private final long startNano;
        private long endNano;

        private MutableSpan(String name, String type, String traceId, String spanId, String parentSpanId,
                            boolean rootObservation, Map<String, String> attributes, long startNano) {
            this.name = name;
            this.type = type;
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.rootObservation = rootObservation;
            this.attributes = attributes;
            this.startNano = startNano;
        }

        private ChatSpan toSpan() {
            Map<String, String> copy = new LinkedHashMap<>(attributes);
            copy.put("duration_ms", Long.toString(Math.max(0L, (endNano - startNano) / 1_000_000L)));
            return new ChatSpan(name, type, traceId, spanId, parentSpanId, rootObservation,
                    Map.copyOf(copy), startNano, Math.max(startNano, endNano));
        }
    }
}
