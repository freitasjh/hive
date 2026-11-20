package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.exceptions.SecurityException;
import br.com.systec.hive.security.api.service.LoginService;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class LoginServiceImpl implements LoginService {
    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final SecurityTokenService securityTokenService;
    private final TokenCacheService tokenCacheService;

    public LoginServiceImpl(AuthenticationManager authenticationManager, SecurityTokenService securityTokenService,
                            TokenCacheService tokenCacheService) {
        this.authenticationManager = authenticationManager;
        this.securityTokenService = securityTokenService;
        this.tokenCacheService = tokenCacheService;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginAuthenticate login(Login login) throws SecurityException {
        try {
            var userAndPasswordToken = new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword());

            var authenticate = authenticationManager.authenticate(userAndPasswordToken);

            return securityTokenService.generateTokenAndRefreshToken(authenticate);
        } catch (AuthenticationException e) {
            log.warn("Falha de autenticação para o usuário: {}", login.getUsername());
            throw new InvalidCredentialsException(e);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new SecurityException("Ocorreu um erro ao tentar gerar o token", e);
        }
    }

    @Override
    @Transactional
    public LoginAuthenticate refreshToken() throws SecurityException {
        return securityTokenService.generateNewAccessTokenByRefreshToken();
    }

    @Override
    public void logout(UUID tenantId, String refreshToken) throws SecurityException {
        if (tenantId == null || refreshToken == null || refreshToken.isBlank()) {
            throw new SecurityException("Dados de logout inválidos");
        }

        tokenCacheService.revokeRefreshToken(tenantId, refreshToken);
    }
}
