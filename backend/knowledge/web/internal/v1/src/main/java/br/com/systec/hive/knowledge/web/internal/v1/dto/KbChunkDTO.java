package br.com.systec.hive.knowledge.web.internal.v1.dto;

import java.util.List;
import java.util.UUID;

public class KbChunkDTO {

    private UUID docId;
    private String docName;
    private UUID chunkId;
    private String text;
    private double score;

    public UUID getDocId() {
        return docId;
    }

    public void setDocId(UUID docId) {
        this.docId = docId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public static KbChunkDTO of(br.com.systec.hive.knowledge.api.domain.KbChunk chunk) {
        KbChunkDTO dto = new KbChunkDTO();
        dto.setDocId(chunk.getDocId());
        dto.setDocName(chunk.getDocName());
        dto.setChunkId(chunk.getChunkId());
        dto.setText(chunk.getText());
        dto.setScore(chunk.getScore());
        return dto;
    }

    public static List<KbChunkDTO> ofList(List<br.com.systec.hive.knowledge.api.domain.KbChunk> chunks) {
        return chunks.stream().map(KbChunkDTO::of).toList();
    }
}
