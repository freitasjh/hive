package br.com.systec.hive.publicapi.api.service;

import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreateDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientGrantsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPageDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPromptsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientResponseDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientRotatedDTO;

/**
 * Superfície de gestão administrativa de clients públicos (ADR-003), na chain humana.
 * O tenant é sempre o da sessão humana — resolvido na implementação, nunca recebido do cliente.
 */
public interface PublicApiClientAdminService {

    PublicApiClientCreatedDTO create(PublicApiClientCreateDTO request);

    PublicApiClientPageDTO list(int page, int size);

    PublicApiClientResponseDTO get(String clientId);

    PublicApiClientRotatedDTO rotate(String clientId);

    void revoke(String clientId);

    PublicApiClientResponseDTO updateGrants(String clientId, PublicApiClientGrantsDTO request);

    PublicApiClientResponseDTO updatePrompts(String clientId, PublicApiClientPromptsDTO request);
}
