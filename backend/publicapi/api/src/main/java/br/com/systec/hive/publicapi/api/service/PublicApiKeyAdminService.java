package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.dto.ApiKeyCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeyRotatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeySummaryDTO;

import java.util.List;

/** ADR-002: ciclo de vida da key complementar na chain humana; tenant sempre da sessão. */
public interface PublicApiKeyAdminService {

    ApiKeyCreatedDTO issue(String clientId);

    List<ApiKeySummaryDTO> list(String clientId);

    ApiKeyRotatedDTO rotate(String clientId, String keyId);

    void revoke(String clientId, String keyId);
}
