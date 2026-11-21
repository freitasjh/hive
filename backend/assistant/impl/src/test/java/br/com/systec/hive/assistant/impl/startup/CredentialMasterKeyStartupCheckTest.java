package br.com.systec.hive.assistant.impl.startup;

import br.com.systec.hive.assistant.impl.converter.AesGcmAttributeConverter;
import br.com.systec.hive.assistant.impl.repository.ProviderCredentialRepository;
import br.com.systec.hive.assistant.impl.security.ProviderMasterKey;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialMasterKeyStartupCheckTest {

    @Mock
    private ProviderCredentialRepository repository;

    private ProviderMasterKey masterKey;
    private String beanKeyBase64;
    private CredentialMasterKeyStartupCheck check;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;
    private String savedProfiles;

    @BeforeEach
    void setUp() {
        savedProfiles = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "test");
        AesGcmAttributeConverter.setTestKey(randomKey());
        beanKeyBase64 = base64Key();
        masterKey = new ProviderMasterKey(new MockEnvironment()
                .withProperty(ProviderMasterKey.MASTER_KEY_PROPERTY, beanKeyBase64));
        check = new CredentialMasterKeyStartupCheck(repository, masterKey);
        logger = (Logger) LoggerFactory.getLogger(CredentialMasterKeyStartupCheck.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        logs.stop();
        AesGcmAttributeConverter.clearTestKey();
        ProviderMasterKey.resetRuntimeKeyForTesting();
        if (savedProfiles == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", savedProfiles);
        }
    }

    @Test
    void when_noCredentialsStored_then_silent() {
        when(repository.findFirstRawCiphertext()).thenReturn(Optional.empty());

        check.run(null);

        assertThat(errorMessages()).isEmpty();
    }

    @Test
    void when_masterKeyMatchesStoredCiphertext_then_silent() {
        String cipher = new AesGcmAttributeConverter().convertToDatabaseColumn("sk-ok");
        when(repository.findFirstRawCiphertext()).thenReturn(Optional.of(cipher));

        check.run(null);

        assertThat(errorMessages()).isEmpty();
    }

    @Test
    void when_masterKeyDivergesFromStoredCiphertext_then_singleErrorBannerWithGuidance() {
        String cipher = new AesGcmAttributeConverter().convertToDatabaseColumn("sk-gravada");
        AesGcmAttributeConverter.setTestKey(randomKey());
        when(repository.findFirstRawCiphertext()).thenReturn(Optional.of(cipher));

        check.run(null);

        assertThat(errorMessages()).hasSize(1);
        String banner = errorMessages().get(0);
        assertThat(banner).contains("PROVIDER_MASTER_KEY");
        assertThat(banner).contains("não corresponde");
        assertThat(banner).contains("não há recuperação automática");
        assertThat(banner).doesNotContain(cipher).doesNotContain("sk-gravada");
    }

    @Test
    void when_masterKeyDiverges_then_bannerNamesTheResolvedSourceWithoutLeakingTheKey() {
        String cipher = new AesGcmAttributeConverter().convertToDatabaseColumn("sk-gravada");
        AesGcmAttributeConverter.setTestKey(randomKey());
        when(repository.findFirstRawCiphertext()).thenReturn(Optional.of(cipher));

        check.run(null);

        assertThat(errorMessages()).hasSize(1);
        String banner = errorMessages().get(0);
        assertThat(banner).contains(ProviderMasterKey.MASTER_KEY_PROPERTY);
        assertThat(banner).doesNotContain(beanKeyBase64);
    }

    @Test
    void when_repositoryUnavailable_then_silentAndDoesNotBreakStartup() {
        when(repository.findFirstRawCiphertext()).thenThrow(new IllegalStateException("db down"));

        check.run(null);

        assertThat(errorMessages()).isEmpty();
    }

    @Test
    void when_masterKeyMissingButCredentialsStored_then_singleErrorBanner() {
        AesGcmAttributeConverter.clearTestKey();
        when(repository.findFirstRawCiphertext()).thenReturn(Optional.of("Y2lwaGVydGV4dC1xdWUtaW5hbGlkYWJseS1wbGFjZWhvbGRlcg=="));

        check.run(null);

        assertThat(errorMessages()).hasSize(1);
        assertThat(errorMessages().get(0)).contains("PROVIDER_MASTER_KEY");
    }

    private List<String> errorMessages() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private static String base64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }
}
