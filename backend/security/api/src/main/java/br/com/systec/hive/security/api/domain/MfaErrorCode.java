package br.com.systec.hive.security.api.domain;

import org.springframework.http.HttpStatus;

/**
 * Códigos semânticos de erro do fluxo MFA. Nenhuma mensagem associada inclui segredo,
 * código ou token — a serialização em Fase 2 usa apenas o nome (ver SPEC §6).
 */
public enum MfaErrorCode {

    MFA_REQUIRED(HttpStatus.UNAUTHORIZED),
    MFA_CHALLENGE_EXPIRED(HttpStatus.UNAUTHORIZED),
    MFA_INVALID_CODE(HttpStatus.UNAUTHORIZED),
    MFA_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS),
    MFA_INVALID_RECOVERY(HttpStatus.UNAUTHORIZED),
    MFA_NOT_ENABLED(HttpStatus.UNAUTHORIZED),
    MFA_SETUP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    MfaErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
