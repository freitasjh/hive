package br.com.systec.hive.retention;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Uma linha JSON por execucao, em stdout, para o agregador. Sem secrets e sem CR/LF injetados. */
final class Audit {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Audit() {}

    static void write(PrintStream out, RetentionConfig config, Result result) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("run_id", config.runId());
        record.put("executed_at", config.now().toString());
        record.put("retention_days", config.retentionDays());
        record.put("candidates", result.candidates());
        record.put("submitted", result.submitted());
        record.put("errors", result.errors());
        record.put("traceIds", result.traceIds());
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("fromStartTime", config.fromStartTime().toString());
        window.put("toStartTime", config.cutoff().toString());
        window.put("basis", "root.startTime");
        record.put("window", window);
        record.put("timestamp", config.now().toString());
        try {
            out.println(JSON.writeValueAsString(record));
        } catch (Exception e) {
            out.println("{\"run_id\":\"unknown\",\"errors\":[\"audit_serialization_failed\"]}");
        }
    }
}
