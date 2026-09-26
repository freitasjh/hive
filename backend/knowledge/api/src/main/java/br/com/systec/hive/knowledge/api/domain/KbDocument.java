package br.com.systec.hive.knowledge.api.domain;

import br.com.systec.hive.commons.api.domain.AbstractDomain;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class KbDocument extends AbstractDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private UUID kbId;
    private KbSourceType sourceType;
    private String sourceUri;
    private String fileName;
    private String sha256;
    private KbDocumentStatus status = KbDocumentStatus.PENDING;
    private String error;

    public UUID getKbId() {
        return kbId;
    }

    public void setKbId(UUID kbId) {
        this.kbId = kbId;
    }

    public KbSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(KbSourceType sourceType) {
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

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public KbDocumentStatus getStatus() {
        return status;
    }

    public void setStatus(KbDocumentStatus status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
