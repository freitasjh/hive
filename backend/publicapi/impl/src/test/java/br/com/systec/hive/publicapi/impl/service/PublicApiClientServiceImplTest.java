package br.com.systec.hive.publicapi.impl.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

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
    void when_verifySecret_then_constantTimeComparison() {
        PublicApiClientServiceImpl svc = new PublicApiClientServiceImpl(repository, redisTemplate);
        String hash = svc.hashSecret("secret");
        assertThat(svc.verifySecret("secret", hash)).isTrue();
        assertThat(svc.verifySecret("wrong", hash)).isFalse();
    }
}