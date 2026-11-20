package br.com.systec.hive.security.api.domain;

import java.util.List;

/**
 * Resultado transitório do setup MFA. Contém o segredo e os códigos de recuperação
 * <strong>em claro</strong> — exibidos uma única vez ao usuário e descartados em seguida.
 *
 * <p>Nunca gravar em banco, cache, URL ou log. Em repouso só existe o ciphertext do segredo
 * e o hash dos códigos de recuperação.
 */
public record MfaSetupResult(
        String secret,
        String qrDataUri,
        List<String> recoveryCodes
) {
    public MfaSetupResult {
        recoveryCodes = List.copyOf(recoveryCodes);
    }
}
