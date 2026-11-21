package br.com.systec.hive.assistant.api.diagnostics;

/**
 * Outbound port for per-message chat telemetry. Implemented by the logging adapter; tests
 * substitute a recorder so assertions target observable behaviour instead of scraping stdout.
 */
public interface ChatTraceRecorder {

    void record(ChatTrace trace);

    /** Used when tracing is not wired. */
    static ChatTraceRecorder noop() {
        return trace -> { };
    }
}
