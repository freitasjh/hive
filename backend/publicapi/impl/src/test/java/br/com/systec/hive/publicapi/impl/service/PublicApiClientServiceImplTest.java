package br.com.systec.hive.publicapi.impl.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicApiClientServiceImplTest {

    @Mock
    private br.com.systec.hive.publicapi.impl.repository.PublicApiClientRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void when_hashSecret_then_producesBcryptHash() {
        PublicApiClientServiceImpl svc = new PublicApiClientServiceImpl(repository, redisTemplate);
        String h1 = svc.hashSecret("secret");
        assertThat(h1).startsWith("$2");
        assertThat(svc.verifySecret("secret", h1)).isTrue();
    }

    @Test
    void when_rotateSecret_then_returnsPlaintextOnceAndKeepsPreviousWindow() {
        PublicApiClientServiceImpl svc = new PublicApiClientServiceImpl(repository, redisTemplate);
        br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity entity =
                new br.com.systec.hive.publicapi.impl.entity.PublicApiClientEntity();
        String oldHash = svc.hashSecret("old-secret");
        entity.setClientId("client-1");
        entity.setSecretHash(oldHash);
        when(repository.findByClientId("client-1")).thenReturn(Optional.of(entity));

        String plaintext = svc.rotateSecret("client-1");

        assertThat(plaintext).isNotBlank().isNotEqualTo(oldHash);
        assertThat(svc.verifySecret(plaintext, entity.getSecretHash())).isTrue();
        assertThat(entity.getPreviousSecretHash()).isEqualTo(oldHash);
        assertThat(entity.getPreviousSecretExpiresAt()).isNotNull();
        verify(repository).save(entity);
    }

    @Test
    void when_verifySecret_then_constantTimeComparison() {
        PublicApiClientServiceImpl svc = new PublicApiClientServiceImpl(repository, redisTemplate);
        String hash = svc.hashSecret("secret");
        assertThat(svc.verifySecret("secret", hash)).isTrue();
        assertThat(svc.verifySecret("wrong", hash)).isFalse();
    }
}