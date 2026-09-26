package br.com.systec.hive.agent.web.internal.v1.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param ragReason machine-readable reason the knowledge context was empty
 *                  ({@code no_link}, {@code kb_not_found_for_tenant}, {@code no_chunk_above_threshold},
 *                  {@code timeout}, {@code error}, {@code rag_disabled}); {@code ok} when RAG
 *                  contributed. Optional on the client, additive on the server.
 */
public record ChatResponseDTO(
        UUID sessionId,
        String role,
        String message,
        Instant timestamp,
        boolean ragUsed,
        List<CitationDTO> citations,
        String ragReason
) {
    public record CitationDTO(UUID docId, String docName, UUID chunkId, double score) {
    }
}
