package br.com.systec.hive.publicapi.api.exceptions;

public class InvalidGrantException extends RuntimeException {
    public InvalidGrantException() {
        super("grant_type must be client_credentials");
    }
}
