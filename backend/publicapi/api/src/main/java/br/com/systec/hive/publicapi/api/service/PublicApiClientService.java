package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.domain.ProvisionedClient;
import java.util.UUID;

public interface PublicApiClientService {
    ProvisionedClient provision(UUID tenantId, String label);
    /** Rotaciona e devolve o plaintext uma única vez; a janela do secret anterior é preservada. */
    String rotateSecret(String clientId);
    void revoke(String clientId);
    boolean verifySecret(String rawSecret, String storedHash);
    String hashSecret(String rawSecret);
}
