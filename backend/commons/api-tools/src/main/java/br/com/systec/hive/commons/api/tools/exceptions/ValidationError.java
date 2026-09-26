package br.com.systec.hive.commons.api.tools.exceptions;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

public class ValidationError extends StandardError {
    @Serial
    private static final long serialVersionUID = -1L;
    private List<FieldMessage> list;

    public ValidationError(Integer status, String msg, Long timeStamp) {
        super(status, msg, timeStamp);
    }

    public ValidationError(Integer status, String title, String detail, String instance, String code) {
        super("about:blank", title, status, detail, instance, code);
    }

    public List<FieldMessage> getErrors() {
        return list != null ? List.copyOf(list) : List.of();
    }

    public void addError(String fieldName, String messagem) {
        if(list == null) {
            list = new ArrayList<>();
        }
        list.add(new FieldMessage(fieldName, messagem));
    }
}