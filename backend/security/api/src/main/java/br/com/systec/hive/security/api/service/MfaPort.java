package br.com.systec.hive.security.api.service;

import java.util.List;

/**
 * Porta de domínio do gerador/verificador TOTP. Declarada na Fase 1 apenas para fixar o
 * contrato; a implementação concreta ({@code TotpAdapter}, isolando a lib 1.7.1) entra na
 * Fase 2. Nenhuma chamada a esta porta existe nesta fase.
 */
public interface MfaPort {

    /** Gera segredo em Base32. */
    String generateSecret();

    /** URI {@code otpauth://} para o app autenticador. */
    String qrDataUri(String secret, String userLabel, String issuer);

    /** Verifica o código com discrepância de ±1 período. */
    boolean verify(String secret, String code);

    /** Códigos de recuperação em claro (exibidos uma única vez). */
    List<String> newRecoveryCodes();
}
