package br.com.systec.hive.security.api.service;

/**
 * Porta de domínio do gerador/verificador TOTP. A implementação concreta
 * ({@code TotpAdapter}, isolando a lib 1.7.1) fica em security/impl.
 *
 * <p><strong>Autoridade dos códigos de recuperação:</strong> {@link MfaCryptoPort#generateRecoveryCodes()}.
 * Esta porta apenas gera/verifica TOTP e produz o QR — não cria códigos de recuperação.
 */
public interface MfaPort {

    /** Gera segredo em Base32. */
    String generateSecret();

    /** URI {@code otpauth://} renderizada como data URI PNG para o app autenticador. */
    String qrDataUri(String secret, String userLabel, String issuer);

    /** Verifica o código com discrepância de ±1 período. */
    boolean verify(String secret, String code);
}
