package br.com.systec.hive.publicapi.api.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ADR-001: costura da key complementar. A implementação real chega na ADR-002; até lá a ausência
 * do bean faz o filtro M2M devolver 401 para qualquer credencial {@code sk-} (fail-closed).
 */
public interface PublicApiKeyAuthenticator {

    AuthenticatedKey authenticate(String rawKey);

    /** Mesmo principal que o filtro hoje extrai do JWT — nenhuma nova lógica de cota ou telemetria. */
    record AuthenticatedKey(UUID tenantId, String clientId, List<String> permissions,
                            List<String> pcode, Map<String, Integer> plock,
                            Map<String, Integer> plim, String keyId,
                            Integer keyChatRequestsPerMinute) {
    }
}
