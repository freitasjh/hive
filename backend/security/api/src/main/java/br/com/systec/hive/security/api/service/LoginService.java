package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.exceptions.SecurityException;

public interface LoginService {

    LoginAuthenticate login(Login login) throws SecurityException;

    LoginAuthenticate refreshToken() throws SecurityException;

    void logout(String refreshToken) throws SecurityException;
}
