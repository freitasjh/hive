package br.com.systec.hive.assistant.impl.diagnostics;

/**
 * Portador de texto do escopo de turno do ADR-001 — distinto de {@code ChatTrace}, que continua
 * textualmente limpo. Um {@code start()} por tentativa impede que texto de uma tentativa vaze para a
 * seguinte num retry.
 */
public final class TextCarrier {

    private static final ThreadLocal<Attempt> CURRENT = new ThreadLocal<>();

    public record RedactedText(String input, String output, boolean failed) {
    }

    private TextCarrier() {
    }

    public static void start() {
        CURRENT.set(new Attempt());
    }

    public static void input(String text) {
        Attempt attempt = CURRENT.get();
        if (attempt != null) {
            attempt.input = text;
        }
    }

    public static void output(String text) {
        Attempt attempt = CURRENT.get();
        if (attempt != null) {
            attempt.output = text;
        }
    }

    /** @return {@code true} somente na primeira falha da tentativa — 1 evento fail-closed. */
    public static boolean markFailed() {
        Attempt attempt = CURRENT.get();
        if (attempt == null || attempt.failed) {
            return false;
        }
        attempt.failed = true;
        return true;
    }

    public static RedactedText take() {
        Attempt attempt = CURRENT.get();
        CURRENT.remove();
        return attempt == null
                ? new RedactedText(null, null, false)
                : new RedactedText(attempt.input, attempt.output, attempt.failed);
    }

    private static final class Attempt {
        private String input;
        private String output;
        private boolean failed;
    }
}
