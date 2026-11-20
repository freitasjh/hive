package br.com.systec.hive.security.impl.security;

import br.com.systec.hive.security.api.service.MfaCryptoPort;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitários da cifra MFA (Fase 1). Nenhum segredo/código é escrito em log.
 */
class MfaCryptoAdapterTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(
            "hive-unit-mfa-master-key-32byte!".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String KEY_B = Base64.getEncoder().encodeToString(
            "other-unit-mfa-master-key-32byte".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static MfaCryptoPort crypto(String key) {
        return new MfaCryptoAdapter(new MfaMasterKey(key, null, false));
    }

    @Test
    void when_encryptThenDecryptWithSameKey_then_roundTrip() {
        String secret = "JBSWY3DPEHPK3PXP";
        MfaCryptoPort port = crypto(KEY_A);

        String ciphertext = port.encrypt(secret);

        assertNotNull(ciphertext);
        assertNotEquals(secret, ciphertext);
        assertEquals(12 + 16 + secret.length(), Base64.getDecoder().decode(ciphertext).length);
        assertEquals(secret, port.decrypt(ciphertext));
    }

    @Test
    void when_twoEncryptionsOfSameSecret_then_ciphertextsDiffer() {
        MfaCryptoPort port = crypto(KEY_A);
        String secret = "JBSWY3DPEHPK3PXP";

        assertNotEquals(port.encrypt(secret), port.encrypt(secret));
    }

    @Test
    void when_decryptWithDifferentKey_then_failClosed() {
        String ciphertext = crypto(KEY_A).encrypt("JBSWY3DPEHPK3PXP");

        assertThrows(IllegalStateException.class, () -> crypto(KEY_B).decrypt(ciphertext));
    }

    @Test
    void when_generateRecoveryCodes_then_eightUniqueCodes() {
        List<String> codes = crypto(KEY_A).generateRecoveryCodes();

        assertEquals(8, codes.size());
        assertEquals(8, codes.stream().distinct().count());
        assertTrue(codes.stream().allMatch(c -> c.matches("[0-9A-F]{16}")));
    }

    @Test
    void when_hashRecoveryCode_then_sha256HexAndStable() {
        MfaCryptoPort port = crypto(KEY_A);
        String code = "ABCDEF0123456789";

        String hash = port.hashRecoveryCode(code);

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        assertEquals(hash, port.hashRecoveryCode(code));
        assertNotEquals(code, hash);
    }

    @Test
    void when_prodProfileWithoutAnyKey_then_bootFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new MfaMasterKey(null, null, true));

        assertTrue(error.getMessage().contains(MfaMasterKey.MASTER_KEY_ENV));
        assertTrue(error.getMessage().contains("produção"));
    }

    @Test
    void when_devProfileWithoutKey_then_noBootFailureButCryptoFailsFast() {
        MfaMasterKey key = new MfaMasterKey(null, null, false);

        assertDoesNotThrow(key::isConfigured);
        assertEquals(false, key.isConfigured());
        assertThrows(IllegalStateException.class, key::secretKeySpec);
        assertThrows(IllegalStateException.class, () -> new MfaCryptoAdapter(key).encrypt("abc"));
    }

    @Test
    void when_malformedKey_then_failWithActionableMessage() {
        assertThrows(IllegalStateException.class, () -> new MfaMasterKey("not-base64!!", null, false));
        assertThrows(IllegalStateException.class,
                () -> new MfaMasterKey(Base64.getEncoder().encodeToString("short".getBytes()), null, false));
    }
}
