package br.com.systec.hive.publicapi.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

public class SystemRoleNotAllowedException extends BaseException {
    public SystemRoleNotAllowedException() {
        super("system_role_not_allowed", HttpStatus.BAD_REQUEST);
    }
}