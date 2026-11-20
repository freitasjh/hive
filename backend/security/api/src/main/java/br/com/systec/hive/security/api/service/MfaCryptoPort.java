package br.com.systec.hive.security.api.service;

import java.util.List;

/**
 * Porta de domínio para cifragem do segredo TOTP e geração/hashing dos códigos de recuperação.
 *
 * <p>A implementação (security/impl) usa AES-256-GCM com chave dedicada {@code MFA_MASTER_KEY},
 * distinta da chave de credenciais de provider. Esta porta não conhece algoritmo nem chave.
 */
public interface MfaCryptoPort {

    /** Cifra o segredo em claro, devolvendo o envelope base64 gravado no banco. */
    String encrypt(String plaintext);

    /** Decifra o envelope; falha com chave divergente (AES-GCM não tem recuperação). */
    String decrypt(String ciphertext);

    /** Gera os códigos de recuperação em claro, a serem exibidos uma única vez no setup. */
    List<String> generateRecoveryCodes();

    /** Hash SHA-256 (hex) do código de recuperação — nunca se grava o código em claro. */
    String hashRecoveryCode(String code);
}
