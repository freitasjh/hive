package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.exceptions.ChatRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ChatRateLimiterTest {

    @InjectMocks
    private ChatRateLimiter limiter;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        limiter.clear();
    }

    @Test
    void when_check_with_tenCalls_then_noException() {
        for (int i = 0; i < 10; i++) {
            limiter.check(tenantId);
        }

        assertThatNoException().isThrownBy(() -> limiter.check(UUID.randomUUID()));
    }

    @Test
    void when_check_with_eleventhCall_then_throwsChatRateLimit() {
        for (int i = 0; i < 10; i++) {
            limiter.check(tenantId);
        }

        assertThatThrownBy(() -> limiter.check(tenantId))
                .isInstanceOf(ChatRateLimitException.class);
    }
}
