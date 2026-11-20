package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.identity.api.domain.MfaRecoveryCodeData;
import br.com.systec.hive.identity.api.domain.MfaSecretData;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.service.MfaRepositoryPort;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.domain.MfaChallenge;
import br.com.systec.hive.security.api.domain.MfaErrorCode;
import br.com.systec.hive.security.api.domain.MfaSetupResult;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.exceptions.MfaException;
import br.com.systec.hive.security.api.service.MfaChallengeCachePort;
import br.com.systec.hive.security.api.service.MfaCryptoPort;
import br.com.systec.hive.security.api.service.MfaPort;
import br.com.systec.hive.security.api.service.MfaServicePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orquestração do gerenciamento MFA (Fase 2).
 *
 * <p><strong>Sem emissão de token.</strong> {@code verify}/{@code recovery} apenas validam o
 * challenge e consomem código/challenge; a emissão do access token entra na Fase 3.
 *
 * <p><strong>Validação de senha no disable:</strong> {@code UserService} não expõe método de
 * validação — usa {@link PasswordEncoder#matches} contra o hash obtido por
 * {@link UserService#findById}. Senha inválida → {@link InvalidCredentialsException} (401).
 *
 * <p><strong>Nenhum log:</strong> secret, código TOTP, recovery code e senha nunca são escritos
 * em log. Erros semânticos usam apenas {@link MfaErrorCode} (sem payload de credencial).
 */
@Service
public class MfaService implements MfaServicePort {

    private static final String ISSUER = "Hive";

    private final MfaPort mfaPort;
    private final MfaCryptoPort cryptoPort;
    private final MfaRepositoryPort repository;
    private final MfaChallengeCachePort challengeCache;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public MfaService(MfaPort mfaPort,
                      MfaCryptoPort cryptoPort,
                      MfaRepositoryPort repository,
                      MfaChallengeCachePort challengeCache,
                      UserService userService,
                      PasswordEncoder passwordEncoder) {
        this.mfaPort = mfaPort;
        this.cryptoPort = cryptoPort;
        this.repository = repository;
        this.challengeCache = challengeCache;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
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
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public void verify(UUID challengeId, UUID tenantId, String code) {
        MfaChallenge challenge = requireValidChallenge(tenantId, challengeId);
        String secret = requireEnabledSecret(challenge.userId(), tenantId);

        if (!mfaPort.verify(secret, code)) {
            handleFailedAttempt(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_INVALID_CODE);
        }
        challengeCache.delete(tenantId, challengeId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recovery(UUID challengeId, UUID tenantId, String recoveryCode) {
        MfaChallenge challenge = requireValidChallenge(tenantId, challengeId);
        requireEnabled(challenge.userId(), tenantId);

        String hash = cryptoPort.hashRecoveryCode(recoveryCode);
        MfaRecoveryCodeData match = repository.findRecoveryCodesByUserId(challenge.userId()).stream()
                .filter(candidate -> candidate.usedAt() == null && candidate.codeHash().equals(hash))
                .findFirst()
                .orElse(null);
        if (match == null) {
            handleFailedAttempt(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_INVALID_RECOVERY);
        }
        // Consumo atômico: o banco só atualiza a linha se usedAt ainda for null.
        // Duas requisições concorrentes com o mesmo código: apenas uma obtém 1.
        if (repository.markRecoveryCodeUsedIfUnused(match.id(), Instant.now()) != 1) {
            handleFailedAttempt(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_INVALID_RECOVERY);
        }
        challengeCache.delete(tenantId, challengeId);
    }

    /** Challenge ausente/expirado → 401; 5 tentativas → 429 + delete. */
    private MfaChallenge requireValidChallenge(UUID tenantId, UUID challengeId) {
        MfaChallenge challenge = challengeCache.get(tenantId, challengeId)
                .orElseThrow(() -> new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED));
        if (challenge.isExpired(Instant.now())) {
            challengeCache.delete(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        if (challenge.attempts() >= MfaChallengeCachePort.MAX_ATTEMPTS) {
            challengeCache.delete(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS);
        }
        return challenge;
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

    /** Tentativa inválida: incrementa; 5ª tentativa estoura o limite e remove o challenge. */
    private void handleFailedAttempt(UUID tenantId, UUID challengeId) {
        int attempts = challengeCache.incrementAttempts(tenantId, challengeId);
        if (attempts < 0) {
            throw new MfaException(MfaErrorCode.MFA_CHALLENGE_EXPIRED);
        }
        if (attempts >= MfaChallengeCachePort.MAX_ATTEMPTS) {
            challengeCache.delete(tenantId, challengeId);
            throw new MfaException(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS);
        }
    }
}
