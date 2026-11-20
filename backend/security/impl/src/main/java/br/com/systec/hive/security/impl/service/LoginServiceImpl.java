package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.LoginResult;
import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.exceptions.SecurityException;
import br.com.systec.hive.security.api.service.LoginService;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Login por senha com desafio MFA (Fase 3).
 *
 * <p>Ramo sem MFA permanece idêntico ao anterior: {@code generateTokenAndRefreshToken(authenticate)}.
 * Com MFA habilitado, a senha correta apenas cria o challenge no Redis e devolve
 * {@link LoginResult.MfaChallengeRequired} — <strong>nenhum access token é emitido</strong>.
 *
 * <p>No ramo sem desafio, o autenticador emitido recebe {@code mfaSetupAvailable}: sinal
 * booleano de onboarding opcional derivado da mesma consulta MFA, sem expor segredo.
 */
@Service
public class LoginServiceImpl implements LoginService {
    private static final Logger log = LoggerFactory.getLogger(LoginServiceImpl.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthenticationManager authenticationManager;
    private final SecurityTokenService securityTokenService;
    private final TokenCacheService tokenCacheService;
    private final MfaRepositoryPort mfaRepository;
    private final MfaChallengeCachePort challengeCache;
    private final HttpServletRequest request;

    public LoginServiceImpl(AuthenticationManager authenticationManager,
                            SecurityTokenService securityTokenService,
                            TokenCacheService tokenCacheService,
                            MfaRepositoryPort mfaRepository,
                            MfaChallengeCachePort challengeCache,
                            HttpServletRequest request) {
        this.authenticationManager = authenticationManager;
        this.securityTokenService = securityTokenService;
        this.tokenCacheService = tokenCacheService;
        this.mfaRepository = mfaRepository;
        this.challengeCache = challengeCache;
        this.request = request;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResult login(Login login) throws SecurityException {
        try {
            var userAndPasswordToken = new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword());

            var authenticate = authenticationManager.authenticate(userAndPasswordToken);

            // Conservador: sem consulta MFA possível, não anunciamos onboarding.
            boolean mfaSetupAvailable = false;
            if (authenticate.getPrincipal() instanceof User user) {
                // Única consulta MFA do login — reaproveitada para o sinal de onboarding.
                Optional<MfaSecretData> mfa = mfaRepository.findByUserIdAndTenantId(
                        user.getId(), Objects.requireNonNull(user.getTenantId(), "tenantId do usuário"));
                if (mfa.isPresent() && mfa.get().enabled()) {
                    return createChallenge(user);
                }
                // Ramo sem desafio: segredo ausente ou desabilitado => setup ainda disponível.
                // Apenas o booleano é propagado; secret/confirmedAt nunca saem do domínio.
                mfaSetupAvailable = mfa.isEmpty() || !mfa.get().enabled();
            }

            LoginAuthenticate issued = securityTokenService.generateTokenAndRefreshToken(authenticate);
            issued.setMfaSetupAvailable(mfaSetupAvailable);
            return new LoginResult.Success(issued);
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

    /** Senha já válida: cria desafio com binding de tenant/IP/UA e nonce de consumo. */
    private LoginResult.MfaChallengeRequired createChallenge(User user) {
        UUID challengeId = UUID.randomUUID();
        String consumeToken = UUID.randomUUID().toString();
        MfaChallenge challenge = new MfaChallenge(
                challengeId, user.getId(), user.getTenantId(),
                ClientContext.remoteIp(request), ClientContext.userAgent(request),
                0, Instant.now().plusSeconds(MfaChallengeCachePort.TTL_SECONDS),
                consumeToken, extractJtiFromRequest());
        challengeCache.save(challenge, consumeToken);
        return new LoginResult.MfaChallengeRequired(challengeId, MfaChallengeCachePort.TTL_SECONDS, challenge.tenantId());
    }

    /** JTI de um token antigo eventualmente enviado no login — blacklistado no verify. */
    private String extractJtiFromRequest() {
        String header = request == null ? null : request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return securityTokenService.extractJti(header.substring(BEARER_PREFIX.length()).trim());
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
