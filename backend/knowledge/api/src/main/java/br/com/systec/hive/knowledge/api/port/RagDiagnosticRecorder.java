package br.com.systec.hive.knowledge.api.port;

import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;

/**
 * Outbound port for RAG observability. Implemented by the logging adapter; tests substitute a
 * recorder so assertions target observable behaviour instead of scraping stdout.
 */
public interface RagDiagnosticRecorder {

    void record(RagDiagnostic diagnostic);

    /** Used when the knowledge module is not on the classpath. */
    static RagDiagnosticRecorder noop() {
        return diagnostic -> { };
    }
}
