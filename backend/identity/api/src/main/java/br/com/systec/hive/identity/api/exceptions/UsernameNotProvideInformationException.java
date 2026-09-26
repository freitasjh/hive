package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UsernameNotProvideInformationException extends UserException {
    @Serial
    private static final long serialVersionUID = -1339142994629964024L;

    public UsernameNotProvideInformationException() {
        super("username.not.provide.information", HttpStatus.BAD_REQUEST);
    }

    public UsernameNotProvideInformationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
