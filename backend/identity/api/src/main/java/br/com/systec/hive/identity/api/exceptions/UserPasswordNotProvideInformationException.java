package br.com.systec.hive.identity.api.exceptions;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class UserPasswordNotProvideInformationException extends UserException {
    @Serial
    private static final long serialVersionUID = -8113864017870784599L;

    public UserPasswordNotProvideInformationException() {
        super("user.password.not.provide.information", HttpStatus.BAD_REQUEST);
    }

    public UserPasswordNotProvideInformationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
