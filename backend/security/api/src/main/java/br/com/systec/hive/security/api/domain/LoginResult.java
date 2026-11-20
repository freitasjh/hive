package br.com.systec.hive.security.api.domain;

import java.util.UUID;

/**
 * Resultado do login: ou autenticação concluída, ou desafio MFA.
 *
 * <p>Tipo selado para que o controller trate os dois ramos de forma exaustiva e nenhum
 * access token seja devolvido no ramo de desafio. Domínio puro, sem dependência de framework.
 */
public sealed interface LoginResult permits LoginResult.Success, LoginResult.MfaChallengeRequired {

    /** Senha válida e MFA desabilitado (ou já superado): contrato de login inalterado. */
    record Success(LoginAuthenticate authenticate) implements LoginResult {
    }

    /**
     * Senha válida, MFA habilitado: nenhum token foi emitido.
     *
     * <p>{@code tenantId} acompanha o desafio para que um navegador fresco consiga submeter
     * o código em {@code /v1/auth/mfa/verify} sem depender de tenant de sessão anterior.
     */
    record MfaChallengeRequired(UUID challengeId, int expiresIn, UUID tenantId) implements LoginResult {
    }
}
