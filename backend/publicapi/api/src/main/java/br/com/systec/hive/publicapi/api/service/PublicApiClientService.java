package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.domain.ProvisionedClient;
import java.util.UUID;

public interface PublicApiClientService {
    ProvisionedClient provision(UUID tenantId, String label);
    void rotateSecret(String clientId);
    void revoke(String clientId);
    boolean verifySecret(String rawSecret, String storedHash);
    String hashSecret(String rawSecret);
}
