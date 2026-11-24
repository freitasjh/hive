package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Plaintext da key APENAS nesta resposta — one-shot. */
public class ApiKeyCreatedDTO {

    private String clientId;
    private String keyId;

    @JsonProperty("api_key")
    private String apiKey;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}
