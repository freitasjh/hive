package br.com.systec.hive.assistant.impl.diagnostics;

import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * Redação antes da serialização OTLP (ADR-003 RF2/RF4): o texto só entra no portador depois de
 * passar pela política. Falha da política marca a tentativa inteira como fail-closed — nunca
 * propaga exceção, nunca registra o texto original em log ou mensagem de erro.
 */
public final class TextRedactor {

    public static final String FAILURE_METRIC = "chat.telemetry.redaction.failures.total";

    private final Function<String, String> policy;
    private final LongAdder failures = new LongAdder();

    public TextRedactor() {
        this(RedactionPolicy::redact);
    }

    /** Injeção de política para os canários de falha; a produção usa a política congelada. */
    public TextRedactor(Function<String, String> policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public void captureInput(String raw) {
        capture(raw, true);
    }

    public void captureOutput(String raw) {
        capture(raw, false);
    }

    /** Contador local do ADR-003; registro/exportação em Micrometer pertence ao ADR-005. */
    public long failureCount() {
        return failures.sum();
    }

    private void capture(String raw, boolean input) {
        try {
            String redacted = raw == null ? null : policy.apply(raw);
            if (raw != null && redacted == null) {
                throw new IllegalStateException("politica de redacao incompleta");
            }
            if (input) {
                TextCarrier.input(redacted);
            } else {
                TextCarrier.output(redacted);
            }
        } catch (RuntimeException ignored) {
            // Sem getMessage(): a exceção pode carregar o texto bruto do provider.
            if (TextCarrier.markFailed()) {
                failures.increment();
            }
        }
    }
}
