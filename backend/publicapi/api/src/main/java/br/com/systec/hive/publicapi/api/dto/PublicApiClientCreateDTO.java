package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload de criação. {@code client_id} e {@code tenantId} NÃO são aceitos: o primeiro é gerado no
 * servidor e o segundo vem da sessão humana. {@code ignoreUnknown=false} rejeita ambos com 400.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class PublicApiClientCreateDTO {

    @NotBlank
    @Size(max = 255)
    private String label;

    private List<String> grants = new ArrayList<>();

    @Valid
    private List<PublicApiClientPromptBindingDTO> prompts = new ArrayList<>();

    @Min(60)
    @Max(3600)
    private Integer tokenTtlSeconds;

    @Min(1)
    private Integer maxMessages;

    @Min(1)
    private Integer maxMessageChars;

    @Min(1)
    private Integer maxTotalChars;

    /** Presentes apenas para detectar e rejeitar campos que pertencem ao servidor. */
    private String clientId;
    private String tenantId;

    @AssertTrue(message = "client_id e tenantId são gerados pelo servidor e não podem ser enviados")
    public boolean isServerOwnedFieldsAbsent() {
        return clientId == null && tenantId == null;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public List<String> getGrants() { return grants; }
    public void setGrants(List<String> grants) { this.grants = grants; }
    public List<PublicApiClientPromptBindingDTO> getPrompts() { return prompts; }
    public void setPrompts(List<PublicApiClientPromptBindingDTO> prompts) { this.prompts = prompts; }
    public Integer getTokenTtlSeconds() { return tokenTtlSeconds; }
    public void setTokenTtlSeconds(Integer tokenTtlSeconds) { this.tokenTtlSeconds = tokenTtlSeconds; }
    public Integer getMaxMessages() { return maxMessages; }
    public void setMaxMessages(Integer maxMessages) { this.maxMessages = maxMessages; }
    public Integer getMaxMessageChars() { return maxMessageChars; }
    public void setMaxMessageChars(Integer maxMessageChars) { this.maxMessageChars = maxMessageChars; }
    public Integer getMaxTotalChars() { return maxTotalChars; }
    public void setMaxTotalChars(Integer maxTotalChars) { this.maxTotalChars = maxTotalChars; }
}
