package br.com.systec.hive.agent.impl.converter;

import br.com.systec.hive.agent.impl.security.ProviderMasterKey;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Converter
public class AesGcmAttributeConverter implements AttributeConverter<String, String> {

    /**
     * Orientação única para chave divergente. Reutilizada no log de falha e no banner de startup
     * para que a ação correta apareça nos dois lugares.
     */
    public static final String MISMATCH_MESSAGE =
            "Falha ao decifrar a credencial de provider: a " + ProviderMasterKey.MASTER_KEY_ENV
                    + " não corresponde às credenciais gravadas (chave diferente da que cifrou). "
                    + "A chave original é irrecuperável e não há recuperação automática: "
                    + "restaure a mesma chave usada na gravação ou "
                    + "re-cadastre as chaves dos providers para que sejam cifradas de novo.";

    private static final Logger log = LoggerFactory.getLogger(AesGcmAttributeConverter.class);

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int KEY_BYTES = ProviderMasterKey.KEY_BYTES;

    private static volatile byte[] testKeyOverride;

    public static void setTestKey(byte[] key) {
        requireTestProfile();
        if (key == null || key.length != KEY_BYTES) {
            throw new IllegalArgumentException("Test key must be 256-bit (32 bytes).");
        }
        testKeyOverride = key.clone();
    }

    public static void clearTestKey() {
        requireTestProfile();
        testKeyOverride = null;
    }

    private static void requireTestProfile() {
        String active = System.getProperty("spring.profiles.active");
        if (active == null || active.isBlank()) {
            active = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (active == null || active.isBlank() || !isTestProfile(active)) {
            throw new IllegalStateException(
                    "Test key override is only allowed with the 'test' profile active.");
        }
    }

    private static boolean isTestProfile(String activeProfiles) {
        for (String profile : activeProfiles.split(",")) {
            if ("test".equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        SecretKeySpec key = keySpec();
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] envelope = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, envelope, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt provider credential.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // Fora do try: falha de configuração da chave (ausente/malformada) tem mensagem própria
        // e não deve ser confundida com "chave divergente das credenciais gravadas".
        SecretKeySpec key = keySpec();
        try {
            byte[] envelope = Base64.getDecoder().decode(dbData);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(envelope, 0, iv, 0, IV_BYTES);
            byte[] ciphertext = new byte[envelope.length - IV_BYTES];
            System.arraycopy(envelope, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Tag mismatch em AES-GCM = chave diferente da que cifrou. A chave original é
            // irrecuperável: só um novo ciclo de cifragem (re-cadastrar a chave) resolve.
            // Log deliberadamente sem a chave, sem parte dela e sem o ciphertext.
            log.error(MISMATCH_MESSAGE, e.getClass().getSimpleName());
            throw new IllegalStateException(MISMATCH_MESSAGE, e);
        }
    }

    /**
     * Chave efetiva da cifragem. O override de teste tem precedência sobre tudo; fora dele, a chave
     * vem do {@link ProviderMasterKey} resolvido no boot (env var &gt; propriedade Spring) — nunca
     * de {@code System.getProperty}, que não enxerga {@code application.properties}.
     */
    private SecretKeySpec keySpec() {
        byte[] override = testKeyOverride;
        if (override != null) {
            return new SecretKeySpec(override, "AES");
        }
        SecretKeySpec key = ProviderMasterKey.runtimeKey();
        if (key == null) {
            throw new IllegalStateException(ProviderMasterKey.NOT_CONFIGURED_MESSAGE);
        }
        return key;
    }
}
