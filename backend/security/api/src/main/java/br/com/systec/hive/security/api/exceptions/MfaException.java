package br.com.systec.hive.security.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.security.api.domain.MfaErrorCode;

import java.io.Serial;
import java.util.Map;

/**
 * Erro semântico do fluxo MFA. A mensagem é fixa por {@link MfaErrorCode} — nunca carrega
 * segredo, código TOTP, código de recuperação, senha ou token (SPEC §6).
 *
 * <p>O status HTTP vem do próprio {@code MfaErrorCode}: 401 para expirado/inválido/não
 * habilitado, 429 para excesso de tentativas, 500 para falha de setup.
 */
public class MfaException extends BaseException {

    @Serial
    private static final long serialVersionUID = 2026112001L;

    private static final Map<MfaErrorCode, String> MESSAGES = Map.of(
            MfaErrorCode.MFA_REQUIRED, "Autenticação MFA necessária.",
            MfaErrorCode.MFA_CHALLENGE_EXPIRED, "Desafio MFA expirado ou inválido.",
            MfaErrorCode.MFA_INVALID_CODE, "Código MFA inválido.",
            MfaErrorCode.MFA_TOO_MANY_ATTEMPTS, "Número máximo de tentativas excedido. Tente novamente mais tarde.",
            MfaErrorCode.MFA_INVALID_RECOVERY, "Código de recuperação inválido ou já utilizado.",
            MfaErrorCode.MFA_NOT_ENABLED, "MFA não habilitado para este usuário.",
            MfaErrorCode.MFA_SETUP_FAILED, "Não foi possível concluir a configuração do MFA."
    );

    private final MfaErrorCode code;

    public MfaException(MfaErrorCode code) {
        super(MESSAGES.get(code), code.httpStatus());
        this.code = code;
    }

    public MfaErrorCode code() {
        return code;
    }
}
