package br.com.systec.hive.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * The tenant has no default LLM configured, so there is nothing to send a turn to.
 *
 * <p>422, not 404 and not 500, and the reason is the shape of the failure: the request is well
 * formed, the tenant is the problem, and the tenant is the one who can fix it. Same class as
 * {@code CONFIG_MISSING} (no credential) and {@code MODEL_UNAVAILABLE} (no such model) — the three
 * say the same thing about three different layers of "this tenant has not finished configuring its
 * LLM", and the chat is the only place that ever notices.
 *
 * <p>The message is the whole point of the class. It names the screen to go to, because a tenant
 * that lands on a bare 422 cannot tell a missing configuration from a rejected one.
 */
public class TenantLlmConfigNotFoundException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TenantLlmConfigNotFoundException() {
        super("Este tenant ainda não tem provedor e modelo definidos. "
                + "Configure em Configurações > Provedores antes de iniciar uma conversa.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
