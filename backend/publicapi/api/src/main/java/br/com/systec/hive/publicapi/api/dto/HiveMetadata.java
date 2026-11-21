package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Conteúdo de {@code metadata.hive}: extensão Hive explicita, não campo OpenAI canônico.
 */
@Schema(name = "HiveMetadata", description = "Hive extension exposed at metadata.hive (not an OpenAI canonical field).")
public class HiveMetadata {

    @JsonProperty("turn_id")
    private String turnId;

    @JsonProperty("prompt_code")
    private String promptCode;

    @JsonProperty("prompt_version")
    private Integer promptVersion;

    @JsonProperty("prompt_fingerprint")
    private String promptFingerprint;

    @JsonProperty("pin_mode")
    private String pinMode;

    private String provider;
    private String model;

    @JsonProperty("elapsed_ms")
    private long elapsedMs;

    @JsonProperty("tenant_id")
    private String tenantId;

    public HiveMetadata() {
    }

    public HiveMetadata(String turnId, String promptCode, Integer promptVersion, String promptFingerprint,
                        String pinMode, String provider, String model, long elapsedMs, String tenantId) {
        this.turnId = turnId;
        this.promptCode = promptCode;
        this.promptVersion = promptVersion;
        this.promptFingerprint = promptFingerprint;
        this.pinMode = pinMode;
        this.provider = provider;
        this.model = model;
        this.elapsedMs = elapsedMs;
        this.tenantId = tenantId;
    }

    public String getTurnId() { return turnId; }
    public void setTurnId(String turnId) { this.turnId = turnId; }
    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public Integer getPromptVersion() { return promptVersion; }
    public void setPromptVersion(Integer promptVersion) { this.promptVersion = promptVersion; }
    public String getPromptFingerprint() { return promptFingerprint; }
    public void setPromptFingerprint(String promptFingerprint) { this.promptFingerprint = promptFingerprint; }
    public String getPinMode() { return pinMode; }
    public void setPinMode(String pinMode) { this.pinMode = pinMode; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
