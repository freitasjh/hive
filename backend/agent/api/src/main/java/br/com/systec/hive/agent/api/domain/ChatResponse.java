package br.com.systec.hive.agent.api.domain;

import br.com.systec.hive.provider.api.ChatRole;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChatResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1340246067280529587L;
    private UUID sessionId;
    private String message;
    private ChatRole role;
    private Instant timestamp;
    private boolean ragUsed;
    private List<br.com.systec.hive.knowledge.api.domain.KbCitation> citations = List.of();

    /**
     * Why no knowledge context was injected, as a stable machine-readable code
     * ({@code no_link}, {@code kb_not_found_for_tenant}, {@code no_chunk_above_threshold},
     * {@code timeout}, {@code error}, {@code rag_disabled}). {@code ok} when RAG contributed.
     *
     * <p>Without it the client only sees {@code ragUsed=false} and the model fills the gap with an
     * invented explanation ("I did not receive any context"), which sends the user debugging the
     * prompt instead of the missing agent→knowledge-base link.
     */
    private String ragReason;


    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ChatRole getRole() {
        return role;
    }

    public void setRole(ChatRole role) {
        this.role = role;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isRagUsed() {
        return ragUsed;
    }

    public void setRagUsed(boolean ragUsed) {
        this.ragUsed = ragUsed;
    }

    public java.util.List<br.com.systec.hive.knowledge.api.domain.KbCitation> getCitations() {
        return citations;
    }

    public void setCitations(java.util.List<br.com.systec.hive.knowledge.api.domain.KbCitation> citations) {
        this.citations = citations;
    }

    public String getRagReason() {
        return ragReason;
    }

    public void setRagReason(String ragReason) {
        this.ragReason = ragReason;
    }
}
