package br.com.systec.hive.ai.api.port;

/**
 * System text for one turn, decided by the caller's prompt policy (ADR-001 AT-02).
 *
 * <p>The streaming gateway needs the same fallback rule as the sync path, but {@code ai/**} is a leaf:
 * it cannot import the assistant's prompt constant. The assistant implements this port and injects it;
 * no prompt text is duplicated in this module.
 */
@FunctionalInterface
public interface TurnPromptPolicy {

    /** Resolves the system text from the selected template text; blank means "use the policy default". */
    String systemFor(String templateText);
}
