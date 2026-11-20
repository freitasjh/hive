package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.MfaSetupResult;

import java.util.UUID;

/**
 * Boundary de gerenciamento MFA consumida por security/web/internal/v1.
 *
 * <p><strong>Fase 2:</strong> {@link #verify} e {@link #recovery} apenas validam e consomem o
 * challenge — <em>nenhum token é emitido</em>. A emissão entra na Fase 3, integrada ao login.
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

    /** Valida o challenge; código inválido incrementa tentativas; sucesso consome o challenge. Sem token. */
    void verify(UUID challengeId, UUID tenantId, String code);

    /** Consome um recovery code (hash, uso único); sucesso consome o challenge. Sem token. */
    void recovery(UUID challengeId, UUID tenantId, String recoveryCode);
}
