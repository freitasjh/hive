package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidGrantException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidScopeException;
import br.com.systec.hive.publicapi.api.service.PublicApiClientService;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientGrantEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientPromptEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientGrantRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientPromptRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicClientTokenIssuer {

    private static final String ISSUER = "hive.api";
    private static final String AUDIENCE = "hive.public";
    private static final String REVOKED_KEY = "hive:public:revoked-clients";


    private final String secret;
    private final PublicApiClientRepository clientRepository;
    private final PublicApiClientGrantRepository grantRepository;
    private final PublicApiClientPromptRepository promptRepository;
    private final PublicApiClientService clientService;
    private final RedisTemplate<String, Object> redisTemplate;

    public PublicClientTokenIssuer(@Value("${api.security.public.token.secret}") String secret,
                                  PublicApiClientRepository clientRepository,
                                  PublicApiClientGrantRepository grantRepository,
                                  PublicApiClientPromptRepository promptRepository,
                                  PublicApiClientService clientService,
                                  RedisTemplate<String, Object> redisTemplate) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("api.security.public.token.secret must not be blank");
        }
        this.secret = secret;
        this.clientRepository = clientRepository;
        this.grantRepository = grantRepository;
        this.promptRepository = promptRepository;
        this.clientService = clientService;
        this.redisTemplate = redisTemplate;
    }

    public String issueToken(String clientId, String rawSecret, String requestedScope) {
        PublicApiClientEntity client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new InvalidClientException("client not found"));

        if (client.getRevokedAt() != null) {
            throw new InvalidClientException("client revoked");
        }
        if (client.getExpiresAt() != null && client.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidClientException("client expired");
        }

        boolean valid = clientService.verifySecret(rawSecret, client.getSecretHash());
        if (!valid && client.getPreviousSecretHash() != null
                && client.getPreviousSecretExpiresAt() != null
                && client.getPreviousSecretExpiresAt().isAfter(LocalDateTime.now())) {
            valid = clientService.verifySecret(rawSecret, client.getPreviousSecretHash());
        }
        if (!valid) {
            throw new InvalidClientException("invalid secret");
        }

        List<String> permissions = grantRepository.findByClientRef(client.getId())
                .stream().map(PublicApiClientGrantEntity::getPermission).collect(Collectors.toList());

        if (requestedScope != null && !requestedScope.trim().isEmpty()) {
            java.util.Set<String> granted = new java.util.HashSet<>(permissions);
            for (String s : requestedScope.trim().split("\\s+")) {
                if (!granted.contains(s)) {
                    throw new InvalidScopeException();
                }
            }
        }

        List<PublicApiClientPromptEntity> prompts = promptRepository.findByClientRef(client.getId());

        List<String> pcode = prompts.stream()
                .map(PublicApiClientPromptEntity::getPromptCode).collect(Collectors.toList());

        Map<String, Integer> plock = new HashMap<>();
        for (PublicApiClientPromptEntity p : prompts) {
            if ("PINNED".equals(p.getPinMode()) && p.getPinnedVersion() != null) {
                plock.put(p.getPromptCode(), p.getPinnedVersion());
            }
        }

        Map<String, Integer> plim = new HashMap<>();
        plim.put("m", client.getMaxMessages());
        plim.put("c", client.getMaxMessageChars());
        plim.put("t", client.getMaxTotalChars());

        Instant now = Instant.now();
        Instant exp = now.plusSeconds(client.getTokenTtlSeconds() == null ? 600 : client.getTokenTtlSeconds());

        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withSubject(clientId)
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("tenant-id", client.getTenantId().toString())
                .withClaim("permissions", permissions)
                .withClaim("pcode", pcode)
                .withClaim("plock", plock)
                .withClaim("plim", plim)
                .withClaim("typ", "JWT")
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(exp))
                .sign(algorithm);
    }

    public String grantedScope(String clientId, String requestedScope) {
        PublicApiClientEntity client = clientRepository.findByClientId(clientId).orElse(null);
        if (client == null) {
            return null;
        }
        List<String> permissions = grantRepository.findByClientRef(client.getId())
                .stream().map(PublicApiClientGrantEntity::getPermission).collect(Collectors.toList());
        java.util.Set<String> granted = new java.util.HashSet<>(permissions);
        if (requestedScope == null || requestedScope.trim().isEmpty()) {
            if (permissions.isEmpty()) {
                return null;
            }
            return String.join(" ", permissions);
        }
        List<String> result = new ArrayList<>();
        for (String s : requestedScope.trim().split("\\s+")) {
            if (granted.contains(s) && !result.contains(s)) {
                result.add(s);
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        return String.join(" ", result);
    }

    public long expiresInSeconds(String token) {
        try {
            DecodedJWT decoded = JWT.decode(token);
            long ms = decoded.getExpiresAt().getTime() - System.currentTimeMillis();
            return Math.max(0, ms / 1000);
        } catch (Exception e) {
            return 600;
        }
    }

    public DecodedJWT decodeAndVerify(String token) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        DecodedJWT decoded = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .withClaim("typ", "JWT")
                .build()
                .verify(token);

        String subject = decoded.getSubject();
        Boolean revoked = redisTemplate.opsForSet().isMember(REVOKED_KEY, subject);
        if (Boolean.TRUE.equals(revoked)) {
            throw new JWTVerificationException("client revoked");
        }
        // immediate revocation check via DB (no negative cache)
        PublicApiClientEntity client = clientRepository.findByClientId(subject).orElse(null);
        if (client != null && client.getRevokedAt() != null) {
            redisTemplate.opsForSet().add(REVOKED_KEY, subject);
            throw new JWTVerificationException("client revoked");
        }
        return decoded;
    }
}