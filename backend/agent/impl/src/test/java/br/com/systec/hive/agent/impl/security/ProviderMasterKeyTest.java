package br.com.systec.hive.agent.impl.security;

import br.com.systec.hive.agent.impl.security.ProviderMasterKey.Source;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderMasterKeyTest {

    @AfterEach
    void tearDown() {
        ProviderMasterKey.resetRuntimeKeyForTesting();
    }

    @Test
    void when_envVarPresent_then_environmentVariableIsUsed() {
        ProviderMasterKey key = new ProviderMasterKey(base64Key(), null);

        assertThat(key.source()).isEqualTo(Source.ENVIRONMENT_VARIABLE);
        assertThat(key.secretKeySpec().getAlgorithm()).isEqualTo("AES");
    }

    @Test
    void when_envVarAbsentAndPropertyPresent_then_springPropertyIsUsed() {
        ProviderMasterKey key = new ProviderMasterKey(null, base64Key());

        assertThat(key.source()).isEqualTo(Source.SPRING_PROPERTY);
    }

    @Test
    void when_envVarBlankAndPropertyPresent_then_springPropertyIsUsed() {
        ProviderMasterKey key = new ProviderMasterKey("   ", base64Key());

        assertThat(key.source()).isEqualTo(Source.SPRING_PROPERTY);
    }

    @Test
    void when_bothPresent_then_environmentVariableHasPrecedence() {
        byte[] fromEnv = randomKey();
        byte[] fromProperty = randomKey();

        ProviderMasterKey key = new ProviderMasterKey(
                Base64.getEncoder().encodeToString(fromEnv),
                Base64.getEncoder().encodeToString(fromProperty));

        assertThat(key.source()).isEqualTo(Source.ENVIRONMENT_VARIABLE);
        assertThat(key.secretKeySpec().getEncoded()).isEqualTo(fromEnv);
    }

    @Test
    void when_environmentHasProperty_then_propertyIsReadFromEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(ProviderMasterKey.MASTER_KEY_PROPERTY, base64Key());

        ProviderMasterKey key = new ProviderMasterKey(environment);

        assertThat(key.source()).isEqualTo(Source.SPRING_PROPERTY);
    }

    @Test
    void when_noSourceConfigured_then_throwsWithActionableMessage() {
        assertThatThrownBy(() -> new ProviderMasterKey(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ProviderMasterKey.MASTER_KEY_PROPERTY)
                .hasMessageContaining(ProviderMasterKey.MASTER_KEY_ENV)
                .hasMessageContaining(ProviderMasterKey.GENERATE_HINT);
    }

    @Test
    void when_valueIsNotBase64_then_throwsNamingTheFormat() {
        assertThatThrownBy(() -> new ProviderMasterKey("nao-e-base64!!", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64")
                .hasMessageContaining(ProviderMasterKey.GENERATE_HINT);
    }

    @Test
    void when_keyIsShorterThan256Bit_then_throwsNamingTheSize() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new ProviderMasterKey(tooShort, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit")
                .hasMessageContaining("32 bytes")
                .hasMessageNotContaining(tooShort);
    }

    @Test
    void when_keyIsLongerThan256Bit_then_throwsNamingTheSize() {
        String tooLong = Base64.getEncoder().encodeToString(new byte[48]);

        assertThatThrownBy(() -> new ProviderMasterKey(null, tooLong))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit");
    }

    @Test
    void when_keyIsSurroundedByWhitespace_then_trimmedBeforeDecoding() {
        String padded = "  " + base64Key() + "  ";

        ProviderMasterKey key = new ProviderMasterKey(padded, null);

        assertThat(key.source()).isEqualTo(Source.ENVIRONMENT_VARIABLE);
    }

    @Test
    void when_toStringCalled_then_neverExposesKeyNorItsFragment() {
        byte[] raw = randomKey();
        String base64 = Base64.getEncoder().encodeToString(raw);
        ProviderMasterKey key = new ProviderMasterKey(base64, null);

        String rendered = key.toString();

        assertThat(rendered).contains(Source.ENVIRONMENT_VARIABLE.name());
        assertThat(rendered).doesNotContain(base64);
        assertThat(rendered).doesNotContain(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
        assertThat(rendered).doesNotContain(base64.substring(0, 8));
        assertThat(rendered).doesNotContain(base64.substring(base64.length() - 8));
    }

    @Test
    void when_keyResolved_then_installedAsRuntimeKeySoEveryConverterInstanceSeesIt() {
        byte[] raw = randomKey();
        new ProviderMasterKey(Base64.getEncoder().encodeToString(raw), null);

        assertThat(ProviderMasterKey.runtimeKey()).isNotNull();
        assertThat(ProviderMasterKey.runtimeKey().getEncoded()).isEqualTo(raw);
    }

    @Test
    void when_resetRuntimeKeyForTesting_then_runtimeKeyIsCleared() {
        new ProviderMasterKey(base64Key(), null);

        ProviderMasterKey.resetRuntimeKeyForTesting();

        assertThat(ProviderMasterKey.runtimeKey()).isNull();
    }

    @Test
    void when_sourceDescriptionRequested_then_namesTheSourceWithoutExposingTheKey() {
        String base64 = base64Key();

        assertThat(new ProviderMasterKey(base64, null).sourceDescription())
                .contains(ProviderMasterKey.MASTER_KEY_ENV)
                .doesNotContain(base64);
        assertThat(new ProviderMasterKey(null, base64).sourceDescription())
                .contains(ProviderMasterKey.MASTER_KEY_PROPERTY)
                .doesNotContain(base64);
    }

    @Test
    void when_twoKeysResolvedIndependently_then_eachKeepsItsOwnSecret() {
        byte[] first = randomKey();
        byte[] second = randomKey();

        ProviderMasterKey a = new ProviderMasterKey(Base64.getEncoder().encodeToString(first), null);
        ProviderMasterKey b = new ProviderMasterKey(Base64.getEncoder().encodeToString(second), null);

        assertThat(Stream.of(a, b))
                .extracting(k -> k.secretKeySpec().getEncoded())
                .containsExactly(first, second);
    }

    private static String base64Key() {
        return Base64.getEncoder().encodeToString(randomKey());
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
