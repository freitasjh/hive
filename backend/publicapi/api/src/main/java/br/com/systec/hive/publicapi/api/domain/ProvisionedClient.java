package br.com.systec.hive.publicapi.api.domain;

public class ProvisionedClient {
    private final String clientId;
    private final String clientSecret;

    public ProvisionedClient(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }

    @Override
    public String toString() {
        return "ProvisionedClient{clientId='" + clientId + "', clientSecret='***'}";
    }
}
