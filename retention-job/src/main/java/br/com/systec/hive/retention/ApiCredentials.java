package br.com.systec.hive.retention;

/** Valor do header Basic vindo de env/secret. Nunca aparece em toString, log ou auditoria. */
public final class ApiCredentials {
    private final String basicValue;

    public ApiCredentials(String basicValue) {
        if (basicValue == null || basicValue.isBlank()) throw new IllegalArgumentException("credencial ausente");
        this.basicValue = basicValue;
    }

    public String basicValue() {
        return basicValue;
    }

    @Override
    public String toString() {
        return "ApiCredentials[redacted]";
    }
}
