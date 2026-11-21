package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.ModelValidation;
import br.com.systec.hive.assistant.api.exceptions.ConfigMissingException;
import br.com.systec.hive.assistant.api.exceptions.ValidateRateLimitException;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.exception.ModelNotSupportedForProviderException;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidateServiceImplTest {

    @Mock
    private LlmClientFactory<ChatModel> llmClientFactory;

    @Mock
    private CredentialResolver credentialResolver;

    @Mock
    private ValidateProbe probe;

    @Mock
    private ValidateRateLimiter rateLimiter;

    @Mock
    private ChatModel chatModel;

    @InjectMocks
    private ValidateServiceImpl service;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void when_validate_with_healthyVendor_then_returnsOk() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenReturn(new CredentialResolver.ResolvedCredential("sk-fake", null));
        when(llmClientFactory.createClient(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), any(), any(), any(), eq("sk-fake"), any()))
                .thenReturn(chatModel);
        when(probe.ping(chatModel)).thenReturn("pong");

        ModelValidation result = service.validate(tenantId, AiProvider.OPENAI, "gpt-4o-mini", null);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getProvider()).isEqualTo(AiProvider.OPENAI);
        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getReason()).isNull();
        verify(rateLimiter).check(tenantId);
    }

    @Test
    void when_validate_with_vendorDown_then_returnsOkFalse() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENAI))
                .thenReturn(new CredentialResolver.ResolvedCredential("sk-fake", null));
        when(llmClientFactory.createClient(any(), any(), any(), any(), any(), any(), any())).thenReturn(chatModel);
        when(probe.ping(chatModel)).thenThrow(new RuntimeException("vendor down"));

        ModelValidation result = service.validate(tenantId, AiProvider.OPENAI, "gpt-4o-mini", null);

        assertThat(result.isOk()).isFalse();
        assertThat(result.getReason()).isEqualTo("connection_failed");
    }

    @Test
    void when_validate_with_modelOutsideCatalog_then_throws400() {
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENAI, "llama3.2", null))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        verify(credentialResolver, never()).resolve(any(), any());
    }

    @Test
    void when_validate_withoutCredential_then_throws422() {
        when(credentialResolver.resolve(tenantId, AiProvider.ANTHROPIC))
                .thenThrow(new ConfigMissingException("ANTHROPIC"));

        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.ANTHROPIC, "claude-3-5-haiku-latest", null))
                .isInstanceOf(ConfigMissingException.class);
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_with_privateBaseUrl_then_throws400() {
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENAI_COMPATIBLE, "mimo-v2.5", "http://169.254.169.254/"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_overRateLimit_then_throws429() {
        org.mockito.Mockito.doThrow(new ValidateRateLimitException()).when(rateLimiter).check(tenantId);

        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENAI, "gpt-4o-mini", null))
                .isInstanceOf(ValidateRateLimitException.class);
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_with_goHealthy_then_returnsOkWithFixedBaseUrl() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClient(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any()))
                .thenReturn(chatModel);
        when(probe.ping(chatModel)).thenReturn("pong");

        ModelValidation result = service.validate(tenantId, AiProvider.OPENCODE_GO, "mimo-v2.5", null);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getProvider()).isEqualTo(AiProvider.OPENCODE_GO);
        verify(rateLimiter).check(tenantId);
    }

    @Test
    void when_validate_with_goCustomBaseUrl_then_throws400() {
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENCODE_GO, "mimo-v2.5", "https://custom.example/v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENCODE_ZEN, "claude-sonnet-4-5", "https://custom.example/v1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_with_goEchoOfFixedVendorUrl_then_acceptedButRequestIgnored() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClient(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any()))
                .thenReturn(chatModel);
        when(probe.ping(chatModel)).thenReturn("pong");

        ModelValidation result = service.validate(tenantId, AiProvider.OPENCODE_GO, "mimo-v2.5",
                "https://opencode.ai/zen/go/v1");

        assertThat(result.isOk()).isTrue();
        ArgumentCaptor<String> baseUrlCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClientFactory).createClient(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(),
                eq("go-fake"), baseUrlCaptor.capture());
        assertThat(baseUrlCaptor.getValue()).isNull();
    }

    @Test
    void when_validate_with_goOverrideAttempt_then_throws400() {
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENCODE_GO, "mimo-v2.5", "https://evil.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixa");
        verify(credentialResolver, never()).resolve(any(), any());
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_with_goDenylistedModel_then_throws400() {
        assertThatThrownBy(() -> service.validate(tenantId, AiProvider.OPENCODE_GO, "grok-4.5", null))
                .isInstanceOf(ModelNotSupportedForProviderException.class);
        verify(llmClientFactory, never()).createClient(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void when_validate_with_upstreamSlowerThanFiveSeconds_then_timeoutStillCoversRealLatency() {
        // 12.42s medido no mimo-v2.5 do OpenCode GO: com 5s o probe era interrompido antes
        // de o vendor responder. O limite precisa caber a latência real de LLM, não a 5s.
        assertThat(ValidateServiceImpl.TIMEOUT_SECONDS).isEqualTo(15L);
    }

    @Test
    void when_validate_with_upstreamSlowButWithinTimeout_then_returnsOk() {
        when(credentialResolver.resolve(tenantId, AiProvider.OPENCODE_GO))
                .thenReturn(new CredentialResolver.ResolvedCredential("go-fake", null));
        when(llmClientFactory.createClient(eq(AiProvider.OPENCODE_GO), eq("mimo-v2.5"), any(), any(), any(), eq("go-fake"), any()))
                .thenReturn(chatModel);
        // 300ms: lento o bastante para exercitar o caminho assíncrono, dentro do timeout.
        when(probe.ping(chatModel)).thenAnswer(invocation -> {
            Thread.sleep(300L);
            return "pong";
        });

        ModelValidation result = service.validate(tenantId, AiProvider.OPENCODE_GO, "mimo-v2.5", null);

        assertThat(result.isOk()).isTrue();
        assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(300L);
        assertThat(result.getReason()).isNull();
    }

    @Test
    void when_validate_then_doesNotHoldDatabaseConnectionWhileWaitingUpstream() {
        // O probe bloqueia numa chamada HTTP externa. Se validate() fosse transacional, a
        // conexão ficaria ociosa no pool por até TIMEOUT_SECONDS. Só leitura de credencial
        // acontece antes, via CredentialResolver (que abre a própria transação curta).
        assertThat(ValidateServiceImpl.class.getAnnotation(Transactional.class)).isNull();
    }
}
