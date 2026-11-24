package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.publicapi.api.dto.ApiKeyCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeyRotatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeySummaryDTO;
import br.com.systec.hive.publicapi.api.exceptions.PublicApiClientNotFoundException;
import br.com.systec.hive.publicapi.api.service.PublicApiKeyAdminService;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientKeyEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientKeyRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** ADR-002: emissão one-shot, rotação com janela fixa de 10 min e revogação Redis + DB. */
@Service
public class PublicApiKeyAdminServiceImpl implements PublicApiKeyAdminService {

    private final PublicApiClientRepository clientRepository;
    private final PublicApiClientKeyRepository keyRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public PublicApiKeyAdminServiceImpl(PublicApiClientRepository clientRepository,
                                        PublicApiClientKeyRepository keyRepository,
                                        RedisTemplate<String, Object> redisTemplate) {
        this.clientRepository = clientRepository;
        this.keyRepository = keyRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ApiKeyCreatedDTO issue(String clientId) {
        PublicApiClientEntity client = owned(clientId);
        String keyId = UUID.randomUUID().toString();
        String plaintext = "sk-" + keyId;
        PublicApiClientKeyEntity key = new PublicApiClientKeyEntity();
        key.setTenantId(client.getTenantId());
        key.setClientRef(client.getId());
        key.setKeyId(keyId);
        key.setKeyHash(encoder.encode(plaintext));
        keyRepository.save(key);

        ApiKeyCreatedDTO dto = new ApiKeyCreatedDTO();
        dto.setClientId(client.getClientId());
        dto.setKeyId(keyId);
        dto.setApiKey(plaintext);
        return dto;
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<ApiKeySummaryDTO> list(String clientId) {
        PublicApiClientEntity client = owned(clientId);
        return keyRepository.findByClientRef(client.getId()).stream()
                .map(key -> {
                    ApiKeySummaryDTO dto = new ApiKeySummaryDTO();
                    dto.setKeyId(key.getKeyId());
                    dto.setCreatedAt(key.getCreateAt());
                    dto.setRevokedAt(key.getRevokedAt());
                    dto.setExpiresAt(key.getExpiresAt());
                    dto.setPreviousKeyExpiresAt(key.getPreviousKeyExpiresAt());
                    dto.setLastUsedAt(key.getLastUsedAt());
                    return dto;
                })
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ApiKeyRotatedDTO rotate(String clientId, String keyId) {
        PublicApiClientEntity client = owned(clientId);
        PublicApiClientKeyEntity key = ownedKey(client, keyId);
        String newKeyId = UUID.randomUUID().toString();
        String plaintext = "sk-" + newKeyId;

        key.setPreviousKeyId(key.getKeyId());
        key.setPreviousKeyHash(key.getKeyHash());
        key.setPreviousKeyExpiresAt(LocalDateTime.now().plusMinutes(10));
        key.setKeyId(newKeyId);
        key.setKeyHash(encoder.encode(plaintext));
        keyRepository.save(key);

        ApiKeyRotatedDTO dto = new ApiKeyRotatedDTO();
        dto.setClientId(client.getClientId());
        dto.setKeyId(newKeyId);
        dto.setApiKey(plaintext);
        dto.setPreviousKeyExpiresAt(key.getPreviousKeyExpiresAt());
        return dto;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revoke(String clientId, String keyId) {
        PublicApiClientEntity client = owned(clientId);
        PublicApiClientKeyEntity key = ownedKey(client, keyId);
        key.setRevokedAt(LocalDateTime.now());
        keyRepository.save(key);
        redisTemplate.opsForSet().add(PublicApiKeyAuthenticatorImpl.REVOKED_KEYS, keyId);
    }

    private PublicApiClientEntity owned(String clientId) {
        UUID tenantId = currentTenantId();
        PublicApiClientEntity client = clientRepository.findByClientId(clientId).orElse(null);
        if (client == null || !tenantId.equals(client.getTenantId())) {
            throw new PublicApiClientNotFoundException();
        }
        return client;
    }

    private PublicApiClientKeyEntity ownedKey(PublicApiClientEntity client, String keyId) {
        PublicApiClientKeyEntity key = keyRepository.findByKeyId(keyId).orElse(null);
        if (key == null || !client.getId().equals(key.getClientRef())) {
            throw new PublicApiClientNotFoundException();
        }
        return key;
    }

    private UUID currentTenantId() {
        try {
            UUID tenantId = TenantContext.getCurrentTenant();
            if (tenantId == null) throw new IllegalStateException("tenant ausente");
            return tenantId;
        } catch (RuntimeException e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
