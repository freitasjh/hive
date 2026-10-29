package br.com.systec.hive.publicapi.api.exceptions;

public class InvalidScopeException extends RuntimeException {
    public InvalidScopeException() {
        super("Requested scope not granted");
    }
}
