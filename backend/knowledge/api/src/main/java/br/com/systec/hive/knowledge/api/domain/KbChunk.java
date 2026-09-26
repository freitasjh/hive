package br.com.systec.hive.knowledge.api.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class KbChunk implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

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
}
