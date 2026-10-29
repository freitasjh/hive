package br.com.systec.hive.publicapi.api.exceptions;

public class InvalidClientException extends RuntimeException {
    public InvalidClientException() {
        super("Invalid client credentials");
    }
    public InvalidClientException(String message) {
        super(message);
    }
}
