package br.com.systec.hive.security.api.service;

import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.exceptions.SecurityException;

import java.util.UUID;

public interface LoginService {

    LoginAuthenticate login(Login login) throws SecurityException;

    LoginAuthenticate refreshToken() throws SecurityException;

    void logout(UUID tenantId, String refreshToken) throws SecurityException;
}
