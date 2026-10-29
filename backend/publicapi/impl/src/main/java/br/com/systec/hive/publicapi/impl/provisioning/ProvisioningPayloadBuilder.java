package br.com.systec.hive.publicapi.impl.provisioning;

import br.com.systec.hive.publicapi.api.domain.ProvisioningPayload;

import java.util.List;
import java.util.Map;

public final class ProvisioningPayloadBuilder {

    private ProvisioningPayloadBuilder() {
    }

    public static ProvisioningPayload build(String clientId, String clientSecret, String baseUrl,
                                           List<String> promptCodes, String pinMode,
                                           int maxMessages, int maxMessageChars, int maxTotalChars) {
        String tokenUrl = baseUrl + "/auth/token";
        Map<String, Integer> limits = Map.of("m", maxMessages, "c", maxMessageChars, "t", maxTotalChars);
        return new ProvisioningPayload(clientId, clientSecret, tokenUrl, baseUrl, promptCodes, limits, pinMode);
    }
}