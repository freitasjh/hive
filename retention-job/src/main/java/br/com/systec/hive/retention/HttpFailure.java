package br.com.systec.hive.retention;

/** Falha HTTP sem corpo: corpo pode conter dado de trace ou credencial, nunca e propagado. */
public final class HttpFailure extends Exception {
    private final int status;

    public HttpFailure(int status, String reason) {
        super(reason);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
