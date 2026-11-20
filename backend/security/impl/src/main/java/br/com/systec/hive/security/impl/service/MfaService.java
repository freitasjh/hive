package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.domain.MfaErrorCode;
import br.com.systec.hive.security.api.domain.MfaSetupResult;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.exceptions.MfaException;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import br.com.systec.hive.security.api.service.MfaCryptoPort;
import br.com.systec.hive.security.api.service.MfaPort;
import br.com.systec.hive.security.api.service.MfaServicePort;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orquestração do MFA: setup/enable/disable e segundo fator do login (Fase 3).
 *
 * <p><strong>Emissão somente após validação:</strong> {@code verify}/{@code recovery} consomem o
 * challenge com compare-and-delete atômico, blacklistam o JTI antigo (se houver) e só então
 * chamam {@link SecurityTokenService#generateTokenAndRefreshToken}.
 *
 * <p><strong>Validação de senha no disable:</strong> {@code UserService} não expõe método de
 * validação — usa {@link PasswordEncoder#matches} contra o hash obtido por
 * {@link UserService#findById}. Senha inválida → {@link InvalidCredentialsException} (401).
 *
 * <p><strong>Nenhum log:</strong> secret, código TOTP, recovery code, senha e token nunca são
 * escritos em log. Erros semânticos usam apenas {@link MfaErrorCode} (sem payload de credencial).
 */
@Service
public class MfaService implements MfaServicePort {

    private static final String ISSUER = "Hive";
    private static final Duration JTI_BLACKLIST_TTL = Duration.ofMinutes(15);

    private final MfaPort mfaPort;
    private final MfaCryptoPort cryptoPort;
    private final MfaRepositoryPort repository;
    private final MfaChallengeCachePort challengeCache;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityTokenService securityTokenService;
    private final TokenCacheService tokenCacheService;
    private final HttpServletRequest request;

    @Autowired
    public MfaService(MfaPort mfaPort,
                      MfaCryptoPort cryptoPort,
                      MfaRepositoryPort repository,
                      MfaChallengeCachePort challengeCache,
                      UserService userService,
                      PasswordEncoder passwordEncoder,
                      SecurityTokenService securityTokenService,
                      TokenCacheService tokenCacheService,
                      HttpServletRequest request) {
        this.mfaPort = mfaPort;
        this.cryptoPort = cryptoPort;
        this.repository = repository;
        this.challengeCache = challengeCache;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.securityTokenService = securityTokenService;
        this.tokenCacheService = tokenCacheService;
        this.request = request;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public MfaSetupResult setup(UUID userId, UUID tenantId) {
        try {
            String secret = mfaPort.generateSecret();
            String qrDataUri = mfaPort.qrDataUri(secret, userId.toString(), ISSUER);
            String ciphertext = cryptoPort.encrypt(secret);

            // Rotação de setup: códigos antigos são descartados antes de gravar os novos.
            repository.deleteRecoveryCodesByUserId(userId);
            repository.save(new MfaSecretData(userId, tenantId, ciphertext, false, null));

            List<String> recoveryCodes = cryptoPort.generateRecoveryCodes();
            Instant now = Instant.now();
            for (String code : recoveryCodes) {
                repository.saveRecoveryCode(new MfaRecoveryCodeData(
                        UUID.randomUUID(), userId, cryptoPort.hashRecoveryCode(code), null, now));
            }
            // Segredo/códigos em claro vivem apenas neste retorno, exibido uma única vez.
            return new MfaSetupResult(secret, qrDataUri, recoveryCodes);
        } catch (MfaException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MfaException(MfaErrorCode.MFA_SETUP_FAILED);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void enable(UUID userId, UUID tenantId, String code) {
        MfaSecretData stored = repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_NOT_ENABLED));
        String secret = cryptoPort.decrypt(stored.secretCiphertext());
        if (!mfaPort.verify(secret, code)) {
            throw new MfaException(MfaErrorCode.MFA_INVALID_CODE);
        }
        Instant confirmedAt = stored.confirmedAt() != null ? stored.confirmedAt() : Instant.now();
        repository.save(new MfaSecretData(userId, tenantId, stored.secretCiphertext(), true, confirmedAt));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void disable(UUID userId, UUID tenantId, String password) {
        repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_NOT_ENABLED));

        User user = userService.findById(userId);
        if (user == null || user.getPassword() == null || password == null
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        repository.deleteRecoveryCodesByUserId(userId);
        repository.deleteByUserId(userId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public LoginAuthenticate verify(UUID challengeId, UUID tenantId, String code) {
        MfaChallenge challenge = requireValidChallenge(tenantId, challengeId);
        String secret = requireEnabledSecret(challenge.userId(), tenantId);

        if (!mfaPort.verify(secret, code)) {
            handleFailedAttempt(tenantId, challenge);
            throw new MfaException(MfaErrorCode.MFA_INVALID_CODE);
        }
        return consumeAndIssue(tenantId, challenge);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public LoginAuthenticate recovery(UUID challengeId, UUID tenantId, String recoveryCode) {
        MfaChallenge challenge = requireValidChallenge(tenantId, challengeId);
        requireEnabled(challenge.userId(), tenantId);

        String hash = cryptoPort.hashRecoveryCode(recoveryCode);
        MfaRecoveryCodeData match = repository.findRecoveryCodesByUserId(challenge.userId()).stream()
                .filter(candidate -> candidate.usedAt() == null && candidate.codeHash().equals(hash))
                .findFirst()
                .orElse(null);
        if (match == null) {
            handleFailedAttempt(tenantId, challenge);
            throw new MfaException(MfaErrorCode.MFA_INVALID_RECOVERY);
        }
        // Consumo atômico: o banco só atualiza a linha se usedAt ainda for null.
        // Duas requisições concorrentes com o mesmo código: apenas uma obtém 1.
        if (repository.markRecoveryCodeUsedIfUnused(match.id(), Instant.now()) != 1) {
            handleFailedAttempt(tenantId, challenge);
            throw new MfaException(MfaErrorCode.MFA_INVALID_RECOVERY);
        }
        return consumeAndIssue(tenantId, challenge);
    }

    /**
     * Consome o challenge por compare-and-delete; só o vencedor blacklisteia o JTI antigo e emite
     * o par access/refresh com o mesmo usuário do login. Falha de consumo = 401, sem token.
     */
    private LoginAuthenticate consumeAndIssue(UUID tenantId, MfaChallenge challenge) {
        if (!challengeCache.deleteIfTokenMatches(tenantId, challenge.id(), challenge.consumeToken())) {
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        if (challenge.jti() != null && !challenge.jti().isBlank()) {
            tokenCacheService.addToBlacklist(tenantId, challenge.jti(), JTI_BLACKLIST_TTL);
        }

        User user = userService.findById(challenge.userId());
        if (user == null || !Objects.equals(user.getTenantId(), tenantId)) {
            throw new MfaException(MfaErrorCode.MFA_NOT_ENABLED);
        }
        return securityTokenService.generateTokenAndRefreshToken(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /** Challenge ausente/expirado → 401; bloqueio/5 tentativas → 429; contexto divergente → 401. */
    private MfaChallenge requireValidChallenge(UUID tenantId, UUID challengeId) {
        MfaChallenge challenge = challengeCache.get(tenantId, challengeId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED));
        if (challengeCache.isUserBlocked(tenantId, challenge.userId())) {
            throw new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS);
        }
        if (challenge.isExpired(Instant.now())) {
            challengeCache.delete(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        if (challenge.attempts() >= MfaChallengeCachePort.MAX_ATTEMPTS) {
            challengeCache.delete(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS);
        }
        requireMatchingContext(challenge);
        return challenge;
    }

    /** Mesmo tenant/IP/UA do login, sem revelar qual atributo divergiu. */
    private void requireMatchingContext(MfaChallenge challenge) {
        String currentIp = ClientContext.remoteIp(request);
        String currentAgent = ClientContext.userAgent(request);
        if (!Objects.equals(challenge.remoteIp(), currentIp)
                || !Objects.equals(challenge.userAgent(), currentAgent)) {
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
    }

    /** Recovery precisa apenas do estado habilitado — não decifra o secret. */
    private void requireEnabled(UUID userId, UUID tenantId) {
        MfaSecretData stored = repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_NOT_ENABLED));
        if (!stored.enabled()) {
            throw new MfaException(MfaErrorCode.MFA_NOT_ENABLED);
        }
    }

    /** Segredo cifrado deve existir e estar habilitado; decifra apenas em memória. */
    private String requireEnabledSecret(UUID userId, UUID tenantId) {
        MfaSecretData stored = repository.findByUserIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_NOT_ENABLED));
        if (!stored.enabled()) {
            throw new MfaException(MfaErrorCode.MFA_NOT_ENABLED);
        }
        return cryptoPort.decrypt(stored.secretCiphertext());
    }

    /** Tentativa inválida: incrementa; 5ª tentativa estoura o limite, remove e bloqueia 15 min. */
    private void handleFailedAttempt(UUID tenantId, MfaChallenge challenge) {
        int attempts = challengeCache.incrementAttempts(tenantId, challenge.id());
        if (attempts < 0) {
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        if (attempts >= MfaChallengeCachePort.MAX_ATTEMPTS) {
            challengeCache.delete(tenantId, challenge.id());
            challengeCache.blockUser(tenantId, challenge.userId());
            throw new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS);
        }
    }
}
