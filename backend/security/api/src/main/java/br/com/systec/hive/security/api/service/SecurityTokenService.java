package br.com.systec.hive.security.api.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.exceptions.SecurityException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public interface SecurityTokenService {

    LoginAuthenticate generateTokenAndRefreshToken(Authentication authentication) throws SecurityException;

    LoginAuthenticate generateNewAccessTokenByRefreshToken() throws BaseException;

    String getSubject(String tokenJWT) throws SecurityException;

    void validateTokenRevoke(String tokenJWT) throws BaseException;

    UUID getTenantId(String token) throws SecurityException;
}
