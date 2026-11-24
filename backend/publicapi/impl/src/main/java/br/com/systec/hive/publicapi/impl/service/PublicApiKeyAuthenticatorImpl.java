package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.publicapi.api.service.PublicApiKeyAuthenticator;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientKeyEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientKeyRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * ADR-002/KEY2-02: ÚNICA implementação da porta da ADR-001. Falha devolve {@code null} — nunca lança
 * e nunca tenta o caminho JWT (o filtro já discriminou o prefixo).
 */
@Service
public class PublicApiKeyAuthenticatorImpl implements PublicApiKeyAuthenticator {

    static final String REVOKED_KEYS = "hive:public:revoked-keys";
    static final String PREFIX = "sk-";
    static final int ROTATION_WINDOW_MINUTES = 10;

    private static final Logger log = LoggerFactory.getLogger(PublicApiKeyAuthenticatorImpl.class);

    private final PublicApiClientKeyRepository keyRepository;
    private final PublicApiClientRepository clientRepository;
    private final PublicClientTokenIssuer tokenIssuer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public PublicApiKeyAuthenticatorImpl(PublicApiClientKeyRepository keyRepository,
                                         PublicApiClientRepository clientRepository,
                                         PublicClientTokenIssuer tokenIssuer,
                                         RedisTemplate<String, Object> redisTemplate) {
        this.keyRepository = keyRepository;
        this.clientRepository = clientRepository;
        this.tokenIssuer = tokenIssuer;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public AuthenticatedKey authenticate(String rawKey) {
        try {
            if (rawKey == null || !rawKey.startsWith(PREFIX) || rawKey.length() == PREFIX.length()) {
                return null;
            }
            String keyId = rawKey.substring(PREFIX.length());
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(REVOKED_KEYS, keyId))) {
                return null;
            }
            // Uma única query nos índices key_id/previous_key_id. Nenhuma varredura da tabela e
            // no máximo um BCrypt: o hash escolhido é o do campo que casou.
            PublicApiClientKeyEntity key = keyRepository.findByKeyIdOrPreviousKeyId(keyId, keyId).orElse(null);
            if (key == null || key.getRevokedAt() != null) {
                if (key != null) {
                    redisTemplate.opsForSet().add(REVOKED_KEYS, key.getKeyId());
                }
                return null;
            }
            if (Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(REVOKED_KEYS, key.getKeyId()))) {
                return null;
            }
            boolean current = keyId.equals(key.getKeyId());
            String hash = current ? key.getKeyHash() : key.getPreviousKeyHash();
            boolean withinWindow = current || (key.getPreviousKeyExpiresAt() != null
                    && key.getPreviousKeyExpiresAt().isAfter(LocalDateTime.now()));
            if (hash == null || !withinWindow || !encoder.matches(rawKey, hash)) {
                return null;
            }
            PublicApiClientEntity client = clientRepository.findById(key.getClientRef()).orElse(null);
            if (client == null || client.getRevokedAt() != null) {
                return null;
            }
            key.setLastUsedAt(LocalDateTime.now());
            keyRepository.save(key);
            PublicClientTokenIssuer.Policy policy = tokenIssuer.policyFor(client);
            return new AuthenticatedKey(client.getTenantId(), client.getClientId(),
                    policy.permissions(), policy.pcode(), policy.plock(), policy.plim(),
                    key.getKeyId(), key.getKeyChatRequestsPerMinute() == null
                            ? 100 : key.getKeyChatRequestsPerMinute());
        } catch (Exception e) {
            // Nunca logar a credencial — somente o tipo do problema.
            log.warn("Public API key rejected: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
