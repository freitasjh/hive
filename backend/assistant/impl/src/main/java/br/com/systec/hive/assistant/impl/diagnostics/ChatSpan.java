package br.com.systec.hive.assistant.impl.diagnostics;

import java.util.Map;

/**
 * Um observation do contrato ADR-001: raiz {@code chat.turn} ou filha {@code chat.provider.call}.
 *
 * <p>Somente metadados — nenhum prompt, resposta ou stack trace entra aqui. A hierarquia e as chaves
 * {@code langfuse.*} são congeladas; {@code provider}, {@code model}, {@code usage.*} e
 * {@code duration_ms} não são chaves Langfuse e podem ser consumidas pelo exporter (ADR-002).
 */
public record ChatSpan(
        String name,
        String type,
        String traceId,
        String spanId,
        String parentSpanId,
        boolean rootObservation,
        Map<String, String> attributes,
        long startTimeUnixNano,
        long endTimeUnixNano) {
}
