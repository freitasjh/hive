package br.com.systec.hive.knowledge.api.domain;

/**
 * Why a RAG resolution ended the way it did.
 *
 * <p>Exists because "no context reached the LLM" is not one event: "the agent has no linked
 * knowledge base", "the linked base belongs to another tenant", "nothing scored above the
 * threshold" and "the retrieval timed out" are four different failures with four different fixes.
 * Returning an empty list for all four makes the flow invisible to the operator.
 */
public enum RagOutcome {

    OK("ok"),
    RAG_DISABLED("rag_disabled"),
    NO_LINK("no_link"),
    KB_NOT_FOUND_FOR_TENANT("kb_not_found_for_tenant"),
    NO_CHUNK_ABOVE_THRESHOLD("no_chunk_above_threshold"),
    DIMENSION_MISMATCH("dimension_mismatch"),
    TIMEOUT("timeout"),
    ERROR("error");

    private final String wireName;

    RagOutcome(String wireName) {
        this.wireName = wireName;
    }

    /** Stable lowercase identifier used in logs and exposed to clients. */
    public String wireName() {
        return wireName;
    }

    public boolean isSuccess() {
        return this == OK;
    }
}
