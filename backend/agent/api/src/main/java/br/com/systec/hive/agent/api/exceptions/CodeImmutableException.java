package br.com.systec.hive.agent.api.exceptions;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class CodeImmutableException extends BaseException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CodeImmutableException() {
        super("CODE_IMMUTABLE: code é imutável após criação e não pode ser alterado", HttpStatus.BAD_REQUEST);
    }
}
