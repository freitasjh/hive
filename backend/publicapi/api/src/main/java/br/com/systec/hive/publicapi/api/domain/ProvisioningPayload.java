package br.com.systec.hive.publicapi.api.domain;

import java.util.List;
import java.util.Map;

public class ProvisioningPayload {
    private final String clientId;
    private final String clientSecret;
    private final String tokenUrl;
    private final String baseUrl;
    private final List<String> promptCodes;
    private final Map<String, Integer> limits;
    private final String pinMode;

    public ProvisioningPayload(String clientId, String clientSecret, String tokenUrl, String baseUrl,
                              List<String> promptCodes, Map<String, Integer> limits, String pinMode) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUrl = tokenUrl;
        this.baseUrl = baseUrl;
        this.promptCodes = promptCodes;
        this.limits = limits;
        this.pinMode = pinMode;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getTokenUrl() { return tokenUrl; }
    public String getBaseUrl() { return baseUrl; }
    public List<String> getPromptCodes() { return promptCodes; }
    public Map<String, Integer> getLimits() { return limits; }
    public String getPinMode() { return pinMode; }

    @Override
    public String toString() {
        return "ProvisioningPayload{clientId='" + clientId + "', clientSecret='***', tokenUrl='" + tokenUrl
                + "', baseUrl='" + baseUrl + "', promptCodes=" + promptCodes + ", limits=" + limits
                + ", pinMode='" + pinMode + "'}";
    }
}
