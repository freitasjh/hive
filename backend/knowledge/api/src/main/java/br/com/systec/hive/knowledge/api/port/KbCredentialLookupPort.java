package br.com.systec.hive.knowledge.api.port;

import java.util.UUID;

public interface KbCredentialLookupPort {

    ResolvedKbCredential resolve(UUID tenantId, String embeddingModel);

    record ResolvedKbCredential(String apiKey, String baseUrl) {
    }
}
