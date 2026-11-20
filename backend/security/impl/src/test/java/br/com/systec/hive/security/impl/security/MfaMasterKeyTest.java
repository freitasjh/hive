package br.com.systec.hive.security.impl.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura do caminho Spring de {@link MfaMasterKey}: detecção do perfil {@code prod} inclusive
 * quando ele vem de {@code spring.profiles.default} e nenhum perfil foi ativado explicitamente.
 */
class MfaMasterKeyTest {

    private static final String VALID_KEY = Base64.getEncoder().encodeToString(
            "hive-unit-mfa-master-key-32byte!".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void when_prodIsDefaultProfileWithoutKey_then_springConstructorFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MfaMasterKey(environment));

        assertTrue(error.getMessage().contains(MfaMasterKey.MASTER_KEY_ENV));
    }

    @Test
    void when_prodIsActiveProfileWithoutKey_then_springConstructorFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThrows(IllegalStateException.class, () -> new MfaMasterKey(environment));
    }

    @Test
    void when_devIsDefaultProfileWithoutKey_then_springConstructorDegradesFailClosed() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("dev");

        MfaMasterKey key = new MfaMasterKey(environment);

        assertFalse(key.isConfigured());
        assertThrows(IllegalStateException.class, key::secretKeySpec);
    }

    @Test
    void when_activeDevOverridesDefaultProdWithoutKey_then_springConstructorDegradesFailClosed() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("prod");
        environment.setActiveProfiles("dev");

        MfaMasterKey key = new MfaMasterKey(environment);

        assertFalse(key.isConfigured());
    }

    @Test
    void when_prodIsDefaultProfileWithKeyProperty_then_springConstructorStarts() {
        MockEnvironment environment = new MockEnvironment();
        environment.setDefaultProfiles("prod");
        environment.setProperty(MfaMasterKey.MASTER_KEY_PROPERTY, VALID_KEY);

        MfaMasterKey key = new MfaMasterKey(environment);

        assertTrue(key.isConfigured());
    }
}
