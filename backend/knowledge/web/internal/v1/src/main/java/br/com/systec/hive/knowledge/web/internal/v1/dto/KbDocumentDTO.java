package br.com.systec.hive.knowledge.web.internal.v1.dto;

import java.util.UUID;

public class KbDocumentDTO {

    private UUID id;
    private UUID kbId;
    private String sourceType;
    private String sourceUri;
    private String fileName;
    private String status;
    private String error;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getKbId() {
        return kbId;
    }

    public void setKbId(UUID kbId) {
        this.kbId = kbId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
