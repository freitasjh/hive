package br.com.systec.hive.ai.api.port;

/** Idempotent disconnect hook — the only point the transport calls on client abort (ADR-001 D5). */
public interface StreamingTurnHandle {

    /**
     * Aborts the provider when supported. On a degraded provider it only silences the sink: the
     * session gate stays held until the provider terminal or the short release window (D7').
     */
    void cancel();
}
