package br.com.systec.hive.integrationtest;

import br.com.systec.hive.agent.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.agent.impl.entity.ProviderCredentialEntity;
import br.com.systec.hive.agent.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.agent.impl.security.ProviderMasterKey;
import br.com.systec.hive.agent.impl.security.ProviderMasterKey.Source;
import br.com.systec.hive.agent.impl.startup.CredentialMasterKeyStartupCheck;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.provider.api.AiProvider;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT decisivo do bug "master key sem caminho declarativo".
 *
 * <p>Cenário exato do usuário: backend subir <strong>sem</strong> {@code PROVIDER_MASTER_KEY} no
 * ambiente, com a chave vindo só da propriedade Spring do profile {@code test}.
 *
 * <p>O modo de falha silencioso que este IT existe para pegar: o Hibernate cria o
 * {@code AttributeConverter} fora do container Spring (construtor no-arg), então injetar o bean por
 * construtor seria ignorado sem nenhum erro visível — a aplicação subiria e a cifragem/decifragem
 * usaria outra fonte. Aqui a prova é direta: algo cifrado <em>apenas</em> com
 * {@link ProviderMasterKey#secretKeySpec()} é decifrado por uma instância crua do converter, e o
 * round trip completo (PUT → base → GET) passa pelo Hibernate materializando a entidade.
 *
 * <p><strong>Requisito:</strong> rode sem {@code PROVIDER_MASTER_KEY} exportada no shell. A
 * primeira asserção falha com mensagem explícita caso contrário.
 */
class MasterKeyDeclarativePathIT extends WebIntegrationTest {

    private static final String PASSWORD = "it-secret-pw";
    private static final String OPENAI_KEY = "sk-test-declarative-4321";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    @Autowired
    private ProviderMasterKey masterKey;

    @Autowired
    private ProviderCredentialRepository providerCredentialRepository;

    @Autowired
    private CredentialMasterKeyStartupCheck startupCheck;

    private ListAppender<ILoggingEvent> startupLogs;
    private Logger startupLogger;

    @BeforeEach
    void attachStartupLogAppender() {
        startupLogger = (Logger) LoggerFactory.getLogger(CredentialMasterKeyStartupCheck.class);
        startupLogs = new ListAppender<>();
        startupLogs.start();
        startupLogger.addAppender(startupLogs);
    }

    @AfterEach
    void detachStartupLogAppender() {
        startupLogger.detachAppender(startupLogs);
        startupLogs.stop();
    }

    @Test
    void when_noEnvVarConfigured_then_keyIsResolvedFromSpringProperty() {
        assertThat(System.getenv(ProviderMasterKey.MASTER_KEY_ENV))
                .as("Este IT valida o caminho declarativo: rode a suíte sem %s exportada no shell",
                        ProviderMasterKey.MASTER_KEY_ENV)
                .isNull();
        assertThat(masterKey.source()).isEqualTo(Source.SPRING_PROPERTY);
        assertThat(masterKey.sourceDescription()).contains(ProviderMasterKey.MASTER_KEY_PROPERTY);
    }

    @Test
    void when_hibernateInstantiatesConverterRaw_then_itDecryptsWithTheBeanKey() throws Exception {
        // Cifrado exclusivamente com a chave do bean...
        String envelope = encryptWithBeanKey(OPENAI_KEY);
        // ...decifrado por uma instância crua do converter: exatamente o que o Hibernate cria.
        assertThat(new AesGcmAttributeConverter().convertToEntityAttribute(envelope))
                .isEqualTo(OPENAI_KEY);
    }

    @Test
    void when_credentialStoredViaApiWithoutEnvVar_then_hibernateReloadsItWithTheBeanKey() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "mk-declarative-" + UUID.randomUUID());

        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.last4").value("4321"));

        // Materializar a entidade dispara o converter do Hibernate. Se ele não usasse a chave do
        // bean, a decifragem estouraria tag mismatch e este assert falharia.
        ProviderCredentialEntity stored = providerCredentialRepository
                .findByTenantIdAndProvider(tenantId, AiProvider.OPENAI)
                .orElseThrow();
        assertThat(stored.getApiKey()).isEqualTo(OPENAI_KEY);

        // E a leitura pela API também passa pelo converter.
        String body = mockMvc.perform(get("/v1/provider-credentials")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.provider == 'OPENAI')].configured").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(OPENAI_KEY);
    }

    @Test
    void when_startupCheckRunsWithDeclarativeKey_then_silentAndNeverLogsTheKey() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = tokenFor(tenantId, "mk-startup-" + UUID.randomUUID());
        mockMvc.perform(put("/v1/provider-credentials/OPENAI")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"" + OPENAI_KEY + "\"}"))
                .andExpect(status().isOk());

        startupCheck.run(null);

        List<String> errors = startupLogs.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(errors).isEmpty();
        assertThat(startupLogs.list.stream().map(ILoggingEvent::getFormattedMessage))
                .noneMatch(msg -> msg != null && msg.contains(Base64.getEncoder()
                        .encodeToString(masterKey.secretKeySpec().getEncoded())));
    }

    private String tokenFor(UUID tenantId, String username) {
        seedTenantAndUser(tenantId, username, PASSWORD);
        return jwtTestHelper.generateToken(username, tenantId);
    }

    /**
     * Cifra usando <em>somente</em> a chave resolvida pelo bean, sem passar pelo converter.
     */
    private String encryptWithBeanKey(String plainText) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey.secretKeySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] envelope = new byte[IV_BYTES + ciphertext.length];
        System.arraycopy(iv, 0, envelope, 0, IV_BYTES);
        System.arraycopy(ciphertext, 0, envelope, IV_BYTES, ciphertext.length);
        return Base64.getEncoder().encodeToString(envelope);
    }
}
