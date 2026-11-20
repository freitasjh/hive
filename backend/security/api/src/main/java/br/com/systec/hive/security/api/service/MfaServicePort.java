package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.MfaSetupResult;

import java.util.UUID;

/**
 * Boundary de gerenciamento MFA consumida por security/web/internal/v1.
 *
 * <p><strong>Fase 3:</strong> {@link #verify} e {@link #recovery} validam e consomem o challenge
 * e, somente após sucesso, emitem o mesmo par access/refresh do login.
 *
 * <p>Operações com escrita usam transação {@code REQUIRED} na implementação; a leitura pura
 * ({@code verify}) usa {@code SUPPORTS}.
 */
public interface MfaServicePort {

    /** Gera secret, cifra, persiste e devolve secret + QR + 8 recovery codes uma única vez. */
    MfaSetupResult setup(UUID userId, UUID tenantId);

    /** Confirma o código TOTP e marca o segredo como habilitado. */
    void enable(UUID userId, UUID tenantId, String code);

    /** Exige senha válida e remove secret + recovery codes. */
    void disable(UUID userId, UUID tenantId, String password);

    /** Valida o código, consome o challenge e emite o token — nunca antes do código válido. */
    LoginAuthenticate verify(UUID challengeId, UUID tenantId, String code);

    /** Consome um recovery code de uso único, consome o challenge e emite o token. */
    LoginAuthenticate recovery(UUID challengeId, UUID tenantId, String recoveryCode);
}
