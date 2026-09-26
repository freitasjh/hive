package br.com.systec.hive.agent.impl.service;

import br.com.systec.hive.agent.api.domain.AgentValidation;
import br.com.systec.hive.agent.api.service.ValidateService;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.api.VendorCatalog;
import br.com.systec.hive.provider.api.security.ProviderBaseUrlGuard;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests vendor connectivity without persisting anything.
 * The apiKey always comes from the tenant credential (never from the request body).
 */
@Service
public class ValidateServiceImpl implements ValidateService {

    private static final Logger log = LoggerFactory.getLogger(ValidateServiceImpl.class);

    /**
     * Limite da chamada upstream no "Testar conexão".
     *
     * <p>15s porque a latência real de um LLM em cold start excede 5s com frequência: o
     * {@code mimo-v2.5} do OpenCode GO respondeu em 12.42s / 1.53s / 3.37s em três
     * medições. Com 5s o probe era interrompido antes de o vendor responder e o teste
     * falhava mesmo com chave válida. O cliente HTTP usa 20s para que este backend
     * consiga responder {@code reason:"timeout"} em vez de o axios abortar.
     *
     * <p>Não é a única defesa: {@code ValidateRateLimiter} continua limitando por tenant.
     */
    static final long TIMEOUT_SECONDS = 15L;

    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final CredentialResolver credentialResolver;
    private final ValidateProbe probe;
    private final ValidateRateLimiter rateLimiter;

    public ValidateServiceImpl(LlmClientFactory<ChatModel> llmClientFactory,
                               CredentialResolver credentialResolver,
                               ValidateProbe probe,
                               ValidateRateLimiter rateLimiter) {
        this.llmClientFactory = llmClientFactory;
        this.credentialResolver = credentialResolver;
        this.probe = probe;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public AgentValidation validate(UUID tenantId, AiProvider provider, String model, String baseUrl) {
        rateLimiter.check(tenantId);
        VendorCatalog.validate(provider, model);
        String requestedBaseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.trim();
        String fixedVendorBaseUrl = VendorCatalog.baseUrlFor(provider);
        if (fixedVendorBaseUrl != null) {
            // GO/Zen: aceita só o eco da constante exibida na UI; qualquer outro valor é override.
            ProviderBaseUrlGuard.validateFixedVendorBaseUrlEcho(provider, requestedBaseUrl);
        } else if (requestedBaseUrl != null) {
            ProviderBaseUrlGuard.validate(requestedBaseUrl);
        }
        CredentialResolver.ResolvedCredential credential = credentialResolver.resolve(tenantId, provider);
        // Para GO/Zen a baseUrl é resolvida pelo catálogo (LangChain4jClientFactory ignora o parâmetro):
        // o valor do request serve só de validação, nunca de resolução.
        String effectiveBaseUrl = (fixedVendorBaseUrl != null || requestedBaseUrl == null)
                ? credential.baseUrl()
                : requestedBaseUrl;

        // Probe de conectividade: sem teto de geração (o probe só quer "respondeu?"), temperature
        // 0.0 para resposta determinística. maxTokens/topP null => default do provider.
        ChatModel client = llmClientFactory.createClient(provider, model.trim(), 0.0, null, null,
                credential.apiKey(), effectiveBaseUrl);

        long start = System.currentTimeMillis();
        // Thread daemon: um read de socket bloqueado não é interrompível por shutdownNow(), então
        // a thread do probe pode sobreviver ao timeout. Sendo daemon, ela não segura o
        // encerramento da JVM nem acumula worker não-join.
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "validate-probe");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<String> future = executor.submit(() -> probe.ping(client));
            future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return AgentValidation.ok(provider, model.trim(), System.currentTimeMillis() - start);
        } catch (TimeoutException e) {
            log.warn("validate timeout tenantId={} provider={} model={}", tenantId, provider, model);
            return AgentValidation.failed(provider, model.trim(), System.currentTimeMillis() - start, "timeout");
        } catch (Exception e) {
            log.warn("validate connection failed tenantId={} provider={} model={}: {}",
                    tenantId, provider, model, e.getClass().getSimpleName());
            return AgentValidation.failed(provider, model.trim(), System.currentTimeMillis() - start, "connection_failed");
        } finally {
            executor.shutdownNow();
        }
    }
}
