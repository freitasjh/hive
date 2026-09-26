package br.com.systec.hive.knowledge.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "kb_document")
public class KbDocumentEntity extends AbstractEntity {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "kb_id", nullable = false)
    private UUID kbId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_uri", length = 2048)
    private String sourceUri;

    @Column(name = "file_name", length = 1024)
    private String fileName;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

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

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
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
