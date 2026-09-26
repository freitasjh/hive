package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.domain.RagDiagnostic;
import br.com.systec.hive.knowledge.api.domain.RagOutcome;
import br.com.systec.hive.knowledge.api.port.RagDiagnosticRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Logging adapter for {@link RagDiagnosticRecorder}.
 *
 * <p>Severity split: a healthy retrieval and "nothing above the threshold" are {@code INFO} — they
 * are normal outcomes, not incidents. A base that is missing for the tenant, a timeout or an
 * unexpected error are {@code WARN} because somebody has to act.
 *
 * <p>The diagnostic is already sanitised by construction (no query text, no chunk content), so the
 * message can be emitted as a flat key=value line.
 */
@Component
public class LoggingRagDiagnosticRecorder implements RagDiagnosticRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingRagDiagnosticRecorder.class);

    @Override
    public void record(RagDiagnostic diagnostic) {
        if (diagnostic == null) {
            return;
        }
        String line = format(diagnostic);
        if (isWarning(diagnostic.outcome())) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }

    static String format(RagDiagnostic diagnostic) {
        if (diagnostic == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(160);
        sb.append("rag outcome=").append(diagnostic.outcome().wireName());
        if (diagnostic.agentId() != null) {
            sb.append(" agentId=").append(diagnostic.agentId());
        }
        sb.append(" tenantId=").append(diagnostic.tenantId());
        sb.append(" linkedKbs=").append(diagnostic.kbIds().size())
                .append(diagnostic.kbIds().isEmpty() ? " kbIds=none" : " kbIds=" + ids(diagnostic));
        sb.append(" foundKbs=").append(diagnostic.foundKbs());
        sb.append(" chunks=").append(diagnostic.chunks());
        if (diagnostic.threshold() != null) {
            sb.append(" threshold=").append(diagnostic.threshold());
        }
        sb.append(" elapsedMs=").append(diagnostic.elapsedMs());
        if (diagnostic.systemBlockChars() != RagDiagnostic.NO_SYSTEM_BLOCK) {
            sb.append(" systemBlockChars=").append(diagnostic.systemBlockChars());
        }
        if (diagnostic.reason() != null) {
            sb.append(" reason=").append(diagnostic.reason());
        }
        if (diagnostic.cause() != null) {
            sb.append(" cause=").append(diagnostic.cause());
        }
        return sb.toString();
    }

    private static String ids(RagDiagnostic diagnostic) {
        return diagnostic.kbIds().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Severity is a rule about <em>returned results</em>: a WARN means "the flow ended empty and
     * only this line says why", so it is reserved for the outcomes nobody escalates.
     *
     * <p>{@link RagOutcome#DIMENSION_MISMATCH} is deliberately absent. It is never returned — both
     * producers record it and immediately throw {@code KbDimensionMismatchException}, which becomes
     * a 400 for the caller and a stack on the error log. A WARN there would compete with that
     * escalation instead of adding to it. The line is still emitted at INFO, and it is the only
     * place carrying {@code agentId}/{@code tenantId}/{@code kbIds}: the exception's message is
     * generic and its stack has no tenant correlation, so dropping the line would lose them.
     */
    static boolean isWarning(RagOutcome outcome) {
        return outcome == RagOutcome.KB_NOT_FOUND_FOR_TENANT
                || outcome == RagOutcome.TIMEOUT
                || outcome == RagOutcome.ERROR;
    }
}
