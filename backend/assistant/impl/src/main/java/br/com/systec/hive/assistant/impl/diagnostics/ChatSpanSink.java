package br.com.systec.hive.assistant.impl.diagnostics;

import java.util.List;

/**
 * Saída das observations de um turno. ADR-001 congela o contrato; o transporte OTLP é ADR-002.
 */
public interface ChatSpanSink {

    void publish(List<ChatSpan> spans);

    static ChatSpanSink noop() {
        return spans -> { };
    }
}
