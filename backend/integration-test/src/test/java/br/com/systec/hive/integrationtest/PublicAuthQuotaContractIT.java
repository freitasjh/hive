package br.com.systec.hive.integrationtest;

import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import br.com.systec.hive.publicapi.web.internal.v1.controller.PublicAuthControllerV1;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-004/Decisão 6: superscreve o 400 registrado pela ADR-001. O handler local devolve 429 com
 * {@code Retry-After} e envelope OpenAI; os limites 30/120/30 não são agregados em headers.
 */
class PublicAuthQuotaContractIT extends WebIntegrationTest {

    @MockitoBean
    private RedisQuotaGuard quotaGuard;

    @Test
    void when_tokenEndpointQuotaExceeded_then_localHandlerReturns429WithRetryAfter() throws Exception {
        doThrow(new QuotaExceededException("cota excedida", 5))
                .when(quotaGuard).checkTokenEndpoint(anyString(), anyString(), anyInt(), anyInt(), anyInt());

        mockMvc.perform(post("/v1/public/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grant_type\":\"client_credentials\",\"client_id\":\"c\",\"client_secret\":\"s\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("quota_exceeded"))
                .andExpect(header().doesNotExist("X-RateLimit-Limit-Requests"));
    }

    @Test
    void when_inspectTokenController_then_localQuotaHandlerExists() {
        boolean declaresQuotaHandler = Arrays.stream(PublicAuthControllerV1.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(ExceptionHandler.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(ExceptionHandler.class).value()))
                .anyMatch(QuotaExceededException.class::equals);

        assertThat(declaresQuotaHandler).isTrue();
    }
}
