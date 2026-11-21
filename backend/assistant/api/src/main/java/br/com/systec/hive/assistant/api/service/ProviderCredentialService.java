package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.assistant.api.domain.ProviderCredential;
import br.com.systec.hive.provider.api.AiProvider;

import java.util.List;
import java.util.UUID;

public interface ProviderCredentialService {

    ProviderCredential upsert(UUID tenantId, AiProvider provider, String apiKey, String baseUrl);

    List<ProviderCredential> listStatuses(UUID tenantId);

    void delete(UUID tenantId, AiProvider provider);
}
