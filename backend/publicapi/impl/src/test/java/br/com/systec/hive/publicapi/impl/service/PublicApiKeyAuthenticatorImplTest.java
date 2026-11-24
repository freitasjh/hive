package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.publicapi.api.service.PublicApiKeyAuthenticator;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity;
import br.com.systec.hive.publicapi.impl.entity.PublicApiClientKeyEntity;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientKeyRepository;
import br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR-002: superfície pública sem varredura — 1 query indexada e no máximo 1 BCrypt. */
@ExtendWith(MockitoExtension.class)
class PublicApiKeyAuthenticatorImplTest {

    private static final String OLD = "sk-old";
    private static final String CURRENT = "sk-current";

    @Mock private PublicApiClientKeyRepository keyRepository;
    @Mock private PublicApiClientRepository clientRepository;
    @Mock private PublicClientTokenIssuer tokenIssuer;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private SetOperations<String, Object> setOperations;

    private PublicApiKeyAuthenticator authenticator;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        authenticator = new PublicApiKeyAuthenticatorImpl(
                keyRepository, clientRepository, tokenIssuer, redisTemplate);
    }

    @Test
    void when_keyIsUnknown_then_returnsNullWithOneIndexedQueryAndNoScan() {
        when(keyRepository.findByKeyIdOrPreviousKeyId(anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(authenticator.authenticate("sk-invented")).isNull();

        verify(keyRepository, times(1)).findByKeyIdOrPreviousKeyId("invented", "invented");
        verify(keyRepository, never()).findAll();
    }

    @Test
    void when_currentKeyMatches_then_returnsPrincipalWithOneQuery() {
        UUID tenant = UUID.randomUUID();
        PublicApiClientKeyEntity key = key("current", encoder.encode(CURRENT), null, null, null, null);
        PublicApiClientEntity client = client(tenant);
        when(keyRepository.findByKeyIdOrPreviousKeyId("current", "current")).thenReturn(Optional.of(key));
        when(clientRepository.findById(key.getClientRef())).thenReturn(Optional.of(client));
        when(tokenIssuer.policyFor(client)).thenReturn(new PublicClientTokenIssuer.Policy(
                List.of("PUBLIC_CHAT_COMPLETE"), List.of("p1"), Map.of(), Map.of("r", 100)));

        PublicApiKeyAuthenticator.AuthenticatedKey result = authenticator.authenticate(CURRENT);

        assertThat(result).isNotNull();
        assertThat(result.clientId()).isEqualTo("client-1");
        assertThat(result.tenantId()).isEqualTo(tenant);
        verify(keyRepository, times(1)).findByKeyIdOrPreviousKeyId("current", "current");
        verify(keyRepository, never()).findAll();
    }

    @Test
    void when_previousKeyInsideWindow_then_authenticates() {
        PublicApiClientKeyEntity key = key("new", encoder.encode(CURRENT), "old", encoder.encode(OLD),
                LocalDateTime.now().plusMinutes(10), null);
        PublicApiClientEntity client = client(UUID.randomUUID());
        when(keyRepository.findByKeyIdOrPreviousKeyId("old", "old")).thenReturn(Optional.of(key));
        when(clientRepository.findById(key.getClientRef())).thenReturn(Optional.of(client));
        when(tokenIssuer.policyFor(client)).thenReturn(new PublicClientTokenIssuer.Policy(
                List.of(), List.of(), Map.of(), Map.of()));

        assertThat(authenticator.authenticate(OLD)).isNotNull();
        verify(keyRepository, never()).findAll();
    }

    @Test
    void when_previousKeyOutsideWindow_then_returnsNull() {
        PublicApiClientKeyEntity key = key("new", encoder.encode(CURRENT), "old", encoder.encode(OLD),
                LocalDateTime.now().minusMinutes(1), null);
        when(keyRepository.findByKeyIdOrPreviousKeyId("old", "old")).thenReturn(Optional.of(key));

        assertThat(authenticator.authenticate(OLD)).isNull();
        verify(keyRepository, never()).findAll();
    }

    private static PublicApiClientKeyEntity key(String keyId, String hash, String previousId,
                                                String previousHash, LocalDateTime previousExpiry,
                                                LocalDateTime revokedAt) {
        PublicApiClientKeyEntity key = new PublicApiClientKeyEntity();
        key.setClientRef(UUID.randomUUID());
        key.setKeyId(keyId);
        key.setKeyHash(hash);
        key.setPreviousKeyId(previousId);
        key.setPreviousKeyHash(previousHash);
        key.setPreviousKeyExpiresAt(previousExpiry);
        key.setRevokedAt(revokedAt);
        return key;
    }

    private static PublicApiClientEntity client(UUID tenant) {
        PublicApiClientEntity client = new PublicApiClientEntity();
        client.setTenantId(tenant);
        client.setClientId("client-1");
        return client;
    }
}
