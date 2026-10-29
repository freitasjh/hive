package br.com.systec.hive.publicapi.impl.entity;

import br.com.systec.hive.commons.impl.entity.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serial;
import java.util.UUID;

@Entity
@Table(name = "public_api_client_prompt")
public class PublicApiClientPromptEntity extends AbstractEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "client_ref", nullable = false)
    private UUID clientRef;

    @Column(name = "prompt_code", nullable = false, length = 255)
    private String promptCode;

    @Column(name = "pin_mode", nullable = false, length = 16)
    private String pinMode;

    @Column(name = "pinned_version")
    private Integer pinnedVersion;

    public UUID getClientRef() { return clientRef; }
    public void setClientRef(UUID clientRef) { this.clientRef = clientRef; }
    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public String getPinMode() { return pinMode; }
    public void setPinMode(String pinMode) { this.pinMode = pinMode; }
    public Integer getPinnedVersion() { return pinnedVersion; }
    public void setPinnedVersion(Integer pinnedVersion) { this.pinnedVersion = pinnedVersion; }
}
