package br.com.systec.hive.security.impl.security;

import br.com.systec.hive.security.api.service.MfaCryptoPort;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Adaptador de cifragem do segredo MFA: AES-256-GCM com a chave dedicada
 * {@link MfaMasterKey}. Envelope = IV (12 bytes) || ciphertext+tag (128 bits), em base64 —
 * mesmo formato de {@code AesGcmAttributeConverter}, porém com chave própria.
 *
 * <p>Nenhum segredo, código ou ciphertext é escrito em log.
 */
@Service
public class MfaCryptoAdapter implements MfaCryptoPort {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_BYTES = 8; // 16 hex chars = 64 bits de entropia

    private final MfaMasterKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaCryptoAdapter(MfaMasterKey masterKey) {
        this.masterKey = masterKey;
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey.secretKeySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, envelope, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, envelope, IV_BYTES, ciphertext.length);
            return Base64.getEncoder().encodeToString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar segredo MFA.", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(ciphertext);
            if (envelope.length <= IV_BYTES) {
                throw new IllegalArgumentException("Envelope MFA inválido.");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(envelope, 0, iv, 0, IV_BYTES);
            byte[] encrypted = new byte[envelope.length - IV_BYTES];
            System.arraycopy(envelope, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey.secretKeySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Tag mismatch = chave divergente da que cifrou. Mensagem sem chave/ciphertext/valor.
            throw new IllegalStateException("Falha ao decifrar segredo MFA: chave divergente ou envelope corrompido.", e);
        }
    }

    @Override
    public List<String> generateRecoveryCodes() {
        byte[] buffer = new byte[RECOVERY_CODE_BYTES];
        return IntStream.range(0, RECOVERY_CODE_COUNT)
                .mapToObj(i -> {
                    secureRandom.nextBytes(buffer);
                    return HexFormat.of().withUpperCase().formatHex(buffer);
                })
                .toList();
    }

    @Override
    public String hashRecoveryCode(String code) {
        if (code == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar hash do código de recuperação.", e);
        }
    }
}
