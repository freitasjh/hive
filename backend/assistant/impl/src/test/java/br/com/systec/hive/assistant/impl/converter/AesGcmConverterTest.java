package br.com.systec.hive.assistant.impl.converter;

import br.com.systec.hive.assistant.impl.security.ProviderMasterKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmConverterTest {

    private AesGcmAttributeConverter converter;
    private String savedProfiles;

    @BeforeEach
    void setUp() {
        savedProfiles = System.getProperty("spring.profiles.active");
        System.setProperty("spring.profiles.active", "test");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AesGcmAttributeConverter.setTestKey(key);
        converter = new AesGcmAttributeConverter();
    }

    @AfterEach
    void tearDown() {
        System.setProperty("spring.profiles.active", "test");
        AesGcmAttributeConverter.clearTestKey();
        ProviderMasterKey.resetRuntimeKeyForTesting();
        if (savedProfiles == null) {
            System.clearProperty("spring.profiles.active");
        } else {
            System.setProperty("spring.profiles.active", savedProfiles);
        }
    }

    @Test
    void when_roundTrip_with_validKey_then_decryptsToOriginal() {
        String plain = "sk-test-fake-key-12345";

        String cipher = converter.convertToDatabaseColumn(plain);

        assertThat(cipher).isNotEqualTo(plain);
        assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo(plain);
    }

    @Test
    void when_encryptTwice_with_samePlain_then_differentCiphertexts() {
        String first = converter.convertToDatabaseColumn("sk-test-fake-key");
        String second = converter.convertToDatabaseColumn("sk-test-fake-key");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void when_convertNull_then_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void when_decryptTamperedCipher_then_throwsWithoutLeakingSecret() {
        String cipher = converter.convertToDatabaseColumn("sk-test-fake-key");
        String tampered = cipher.substring(0, cipher.length() - 4) + "AAAA";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("sk-test-fake-key");
    }

    @Test
    void when_decryptWithWrongKey_then_throws() {
        String cipher = converter.convertToDatabaseColumn("sk-test-fake-key");
        byte[] other = new byte[32];
        new SecureRandom().nextBytes(other);
        AesGcmAttributeConverter.setTestKey(other);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(cipher))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void when_decryptWithDivergentMasterKey_then_messageNamesEnvVarAndSaysRecoveryIsImpossible() {
        // Chave A cifrou...
        String cipher = converter.convertToDatabaseColumn("sk-test-fake-key");
        // ...e a chave B, divergente, ficou ativa.
        AesGcmAttributeConverter.setTestKey(randomKey());

        assertThatThrownBy(() -> converter.convertToEntityAttribute(cipher))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROVIDER_MASTER_KEY")
                .hasMessageContaining("não corresponde")
                .hasMessageContaining("não há recuperação automática");
    }

    @Test
    void when_decryptWithDivergentMasterKey_then_neverLeaksKeyOrCiphertext() {
        String cipher = converter.convertToDatabaseColumn("sk-test-fake-key");
        byte[] other = randomKey();
        AesGcmAttributeConverter.setTestKey(other);
        String base64Other = Base64.getEncoder().encodeToString(other);

        Throwable thrown = null;
        try {
            converter.convertToEntityAttribute(cipher);
        } catch (Throwable t) {
            thrown = t;
        }

        assertThat(thrown).isNotNull();
        StringBuilder everything = new StringBuilder();
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            everything.append(t.getMessage()).append(' ').append(t.getClass().getName());
        }
        // Nem a chave (nem parte dela), nem o ciphertext, nem o plaintext.
        assertThat(everything).doesNotContain(base64Other);
        assertThat(everything).doesNotContain(Base64.getEncoder().encodeToString(other));
        assertThat(everything).doesNotContain(cipher);
        assertThat(everything).doesNotContain("sk-test-fake-key");
    }

    @Test
    void when_masterKeyMissing_then_reportsConfigurationNotDivergence() {
        AesGcmAttributeConverter.clearTestKey();
        ProviderMasterKey.resetRuntimeKeyForTesting();

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("sk-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProviderMasterKey.MASTER_KEY_PROPERTY)
                .hasMessageContaining(ProviderMasterKey.MASTER_KEY_ENV)
                .hasMessageContaining(ProviderMasterKey.GENERATE_HINT)
                .hasMessageNotContaining("não corresponde");
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void when_noKeyConfigured_then_throwsIllegalState() {
        AesGcmAttributeConverter.clearTestKey();
        ProviderMasterKey.resetRuntimeKeyForTesting();

        assertThatThrownBy(() -> converter.convertToDatabaseColumn("sk-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProviderMasterKey.MASTER_KEY_ENV);
    }

    @Test
    void when_noOverrideButBeanKeyInstalled_then_usesTheBeanKey() {
        AesGcmAttributeConverter.clearTestKey();
        byte[] beanKey = randomKey();
        new ProviderMasterKey(new MockEnvironment()
                .withProperty(ProviderMasterKey.MASTER_KEY_PROPERTY,
                        Base64.getEncoder().encodeToString(beanKey)));
        // Instância crua: é assim que o Hibernate cria o AttributeConverter.
        AesGcmAttributeConverter plain = new AesGcmAttributeConverter();

        String cipher = plain.convertToDatabaseColumn("sk-declarative");

        assertThat(plain.convertToEntityAttribute(cipher)).isEqualTo("sk-declarative");
    }

    @Test
    void when_testKeyOverrideActive_then_precedesTheBeanKey() {
        byte[] override = randomKey();
        byte[] beanKey = randomKey();
        AesGcmAttributeConverter.setTestKey(override);
        new ProviderMasterKey(new MockEnvironment()
                .withProperty(ProviderMasterKey.MASTER_KEY_PROPERTY,
                        Base64.getEncoder().encodeToString(beanKey)));

        String cipher = new AesGcmAttributeConverter().convertToDatabaseColumn("sk-override");

        // Cifrado com o override: o holder do bean não interfere.
        AesGcmAttributeConverter.clearTestKey();
        AesGcmAttributeConverter.setTestKey(override);
        assertThat(new AesGcmAttributeConverter().convertToEntityAttribute(cipher))
                .isEqualTo("sk-override");
    }

    @Test
    void when_testKeyHasInvalidLength_then_throwsOnSetup() {
        assertThatThrownBy(() -> AesGcmAttributeConverter.setTestKey(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_ciphertextIsValidBase64_then_decodableEnvelope() {
        String cipher = converter.convertToDatabaseColumn("sk-test");

        assertThat(Base64.getDecoder().decode(cipher).length).isGreaterThan(12);
    }

    @Test
    void when_setTestKey_with_nonTestProfile_then_throws() {
        System.setProperty("spring.profiles.active", "prod");

        assertThatThrownBy(() -> AesGcmAttributeConverter.setTestKey(new byte[32]))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");
    }

    @Test
    void when_clearTestKey_with_nonTestProfile_then_throws() {
        System.setProperty("spring.profiles.active", "prod");

        assertThatThrownBy(AesGcmAttributeConverter::clearTestKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");
    }

    @Test
    void when_setTestKey_with_noActiveProfile_then_throws() {
        System.clearProperty("spring.profiles.active");

        assertThatThrownBy(() -> AesGcmAttributeConverter.setTestKey(new byte[32]))
                .isInstanceOf(IllegalStateException.class);
    }
}
