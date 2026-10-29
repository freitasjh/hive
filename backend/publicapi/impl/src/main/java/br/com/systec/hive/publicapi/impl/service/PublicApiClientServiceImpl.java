package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.publicapi.api.domain.ProvisionedClient;
import br.com.systec.hive.publicapi.api.service.PublicApiClientService;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import br.com.systec.hive.commons.api.exceptions.BaseException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class PublicApiClientServiceImpl implements PublicApiClientService {
    private static final Logger log = LoggerFactory.getLogger(PublicApiClientServiceImpl.class);
    private static final String REVOKED_KEY = "hive:public:revoked-clients";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PublicApiClientRepository repository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PasswordEncoder encoder;

    public PublicApiClientServiceImpl(PublicApiClientRepository repository, RedisTemplate<String, Object> redisTemplate) {
        this(repository, redisTemplate, new BCryptPasswordEncoder());
    }

    public PublicApiClientServiceImpl(PublicApiClientRepository repository, RedisTemplate<String, Object> redisTemplate, PasswordEncoder encoder) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.encoder = encoder;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ProvisionedClient provision(UUID tenantId, String label) {
        if (label == null || label.trim().isEmpty()) {
            throw new IllegalArgumentException("label must not be blank");
        }
        String clientId = UUID.randomUUID().toString();
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        PublicApiClientEntity entity = new PublicApiClientEntity();
        entity.setTenantId(tenantId);
        entity.setClientId(clientId);
        entity.setLabel(label);
        entity.setSecretHash(hashSecret(rawSecret));
        entity.setRateLimitPerMinute(60);
        entity.setTokenTtlSeconds(600);
        entity.setMaxMessages(200);
        entity.setMaxMessageChars(32000);
        entity.setMaxTotalChars(256000);
        repository.save(entity);

        return new ProvisionedClient(clientId, rawSecret);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void rotateSecret(String clientId) {
        PublicApiClientEntity entity = repository.findByClientId(clientId)
                .orElseThrow(() -> new BaseException("Public API client not found", HttpStatus.NOT_FOUND));
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        entity.setPreviousSecretHash(entity.getSecretHash());
        entity.setPreviousSecretExpiresAt(LocalDateTime.now().plusMinutes(10));
        entity.setSecretHash(hashSecret(rawSecret));
        repository.save(entity);
        // raw secret not returned (not recoverable); caller must use provision flow for new raw
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void revoke(String clientId) {
        PublicApiClientEntity entity = repository.findByClientId(clientId)
                .orElseThrow(() -> new BaseException("Public API client not found", HttpStatus.NOT_FOUND));
        entity.setRevokedAt(LocalDateTime.now());
        repository.save(entity);
        redisTemplate.opsForSet().add(REVOKED_KEY, entity.getClientId());
    }

    @Override
    public boolean verifySecret(String rawSecret, String storedHash) {
        if (storedHash == null || rawSecret == null) {
            return false;
        }
        return encoder.matches(rawSecret, storedHash);
    }

    @Override
    public String hashSecret(String rawSecret) {
        return encoder.encode(rawSecret);
    }
}