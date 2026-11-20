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
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD do serviço MFA (Fase 3): verify/recovery emitem token somente após consumo atômico
 * do challenge, com binding de IP/UA e blacklist do JTI antigo.
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CHALLENGE_ID = UUID.randomUUID();
    private static final UUID RECOVERY_ID = UUID.randomUUID();
    private static final String SECRET = "JBSWY3DPEHPK3PXP";
    private static final String CIPHER = "cipher-base64-envelope";
    private static final String QR = "data:image/png;base64,QUJD";
    private static final String CODE = "123456";
    private static final String RECOVERY_CODE = "A1B2C3D4E5F60718";
    private static final String RECOVERY_HASH = "hash-do-codigo-de-recuperacao";
    private static final String CONSUME_TOKEN = "nonce-opaco-de-consumo";
    private static final String OLD_JTI = "jti-do-access-token-antigo";
    private static final String ACCESS_TOKEN = "access-token-new";
    private static final String REFRESH_TOKEN = "refresh-token-new";
    private static final LoginAuthenticate ISSUED = new LoginAuthenticate(
            USER_ID, ACCESS_TOKEN, "Bearer", "", REFRESH_TOKEN);

    @Mock MfaPort mfaPort;
    @Mock MfaCryptoPort cryptoPort;
    @Mock MfaRepositoryPort repository;
    @Mock MfaChallengeCachePort challengeCache;
    @Mock UserService userService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SecurityTokenService securityTokenService;
    @Mock TokenCacheService tokenCacheService;
    @Mock HttpServletRequest request;

    @InjectMocks MfaService service;

    @BeforeEach
    void setUp() {
        // Nada a fazer: mocks criados pelo MockitoExtension.
    }

    private static MfaSecretData secretData(boolean enabled) {
        return new MfaSecretData(USER_ID, TENANT_ID, CIPHER, enabled,
                enabled ? Instant.now().minus(1, ChronoUnit.MINUTES) : null);
    }

    private static MfaChallenge challenge(int attempts, long expiresInSeconds) {
        return challenge(CHALLENGE_ID, attempts, expiresInSeconds, CONSUME_TOKEN, null);
    }

    private static MfaChallenge challenge(UUID id, int attempts, long expiresInSeconds,
                                          String consumeToken, String jti) {
        return new MfaChallenge(id, USER_ID, TENANT_ID, "127.0.0.1", "it-agent",
                attempts, Instant.now().plusSeconds(expiresInSeconds), consumeToken, jti);
    }

    private void stubEnabledSecret() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(true)));
        when(cryptoPort.decrypt(CIPHER)).thenReturn(SECRET);
    }

    /** Recovery só verifica o estado habilitado — não deve decifrar o secret. */
    private void stubEnabledOnly() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(true)));
    }

    private void stubChallenge(int attempts, long expiresInSeconds) {
        when(challengeCache.get(TENANT_ID, CHALLENGE_ID))
                .thenReturn(Optional.of(challenge(attempts, expiresInSeconds)));
    }

    /** Contexto de rede igual ao do challenge criado no login. */
    private void stubMatchingContext() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("it-agent");
    }

    /** Consumo atômico bem-sucedido + emissão de token idêntica à do login. */
    private void stubSuccessfulConsume() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        when(userService.findById(USER_ID)).thenReturn(user);
        when(challengeCache.deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN)).thenReturn(true);
        when(securityTokenService.generateTokenAndRefreshToken(any())).thenReturn(ISSUED);
    }

    // ---------- setup ----------

    @Test
    void when_setup_then_persistsEncryptedSecretAndReturnsEightRecoveryCodesOnce() {
        List<String> codes = List.of("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8");
        when(mfaPort.generateSecret()).thenReturn(SECRET);
        when(mfaPort.qrDataUri(SECRET, USER_ID.toString(), "Hive")).thenReturn(QR);
        when(cryptoPort.encrypt(SECRET)).thenReturn(CIPHER);
        when(cryptoPort.generateRecoveryCodes()).thenReturn(codes);
        when(cryptoPort.hashRecoveryCode(anyString()))
                .thenAnswer(inv -> "hash-" + inv.getArgument(0));

        MfaSetupResult result = service.setup(USER_ID, TENANT_ID);

        assertEquals(SECRET, result.secret());
        assertEquals(QR, result.qrDataUri());
        assertEquals(8, result.recoveryCodes().size());
        assertEquals(codes, result.recoveryCodes());
        verify(repository).deleteRecoveryCodesByUserId(USER_ID);
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(s ->
                CIPHER.equals(s.secretCiphertext()) && !s.enabled()
                        && !s.secretCiphertext().contains(SECRET)
                        && USER_ID.equals(s.userId()) && TENANT_ID.equals(s.tenantId())));
        verify(repository, times(8)).saveRecoveryCode(any());
        verifyNoTokenIssued();
    }

    @Test
    void when_setupEncryptionFails_thenMfaSetupFailedWithoutPlaintextPersistence() {
        when(mfaPort.generateSecret()).thenReturn(SECRET);
        when(cryptoPort.encrypt(SECRET)).thenThrow(new IllegalStateException("crypto failure"));

        MfaException ex = assertThrows(MfaException.class, () -> service.setup(USER_ID, TENANT_ID));

        assertEquals(MfaErrorCode.MFA_SETUP_FAILED, ex.code());
        verify(repository, never()).save(any());
        verify(repository, never()).saveRecoveryCode(any());
    }

    // ---------- enable ----------

    @Test
    void when_enableWithValidCode_thenMarksEnabledAndConfirmed() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(false)));
        when(cryptoPort.decrypt(CIPHER)).thenReturn(SECRET);
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        service.enable(USER_ID, TENANT_ID, CODE);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(s ->
                s.enabled() && s.confirmedAt() != null && CIPHER.equals(s.secretCiphertext())));
    }

    @Test
    void when_enableWithInvalidCode_thenMfaInvalidCodeAndNoSave() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(false)));
        when(cryptoPort.decrypt(CIPHER)).thenReturn(SECRET);
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.enable(USER_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_CODE, ex.code());
        verify(repository, never()).save(any());
    }

    @Test
    void when_enableWithoutExistingSecret_thenMfaNotEnabled() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());

        MfaException ex = assertThrows(MfaException.class,
                () -> service.enable(USER_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_NOT_ENABLED, ex.code());
        verify(mfaPort, never()).verify(anyString(), anyString());
    }

    // ---------- disable ----------

    @Test
    void when_disableWithValidPassword_thenRemovesSecretAndRecoveryCodes() {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword("bcrypt-hash");
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(true)));
        when(userService.findById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("raw-password", "bcrypt-hash")).thenReturn(true);

        service.disable(USER_ID, TENANT_ID, "raw-password");

        verify(repository).deleteRecoveryCodesByUserId(USER_ID);
        verify(repository).deleteByUserId(USER_ID);
    }

    @Test
    void when_disableWithWrongPassword_thenInvalidCredentialsAndNothingRemoved() {
        User user = new User();
        user.setId(USER_ID);
        user.setPassword("bcrypt-hash");
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID))
                .thenReturn(Optional.of(secretData(true)));
        when(userService.findById(USER_ID)).thenReturn(user);
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> service.disable(USER_ID, TENANT_ID, "wrong"));

        verify(repository, never()).deleteByUserId(any());
        verify(repository, never()).deleteRecoveryCodesByUserId(any());
    }

    @Test
    void when_disableWithoutExistingSecret_thenMfaNotEnabled() {
        when(repository.findByUserIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());

        MfaException ex = assertThrows(MfaException.class,
                () -> service.disable(USER_ID, TENANT_ID, "raw-password"));

        assertEquals(MfaErrorCode.MFA_NOT_ENABLED, ex.code());
        verifyNoInteractions(userService, passwordEncoder);
    }

    // ---------- verify ----------

    @Test
    void when_verifyWithValidCode_thenConsumesChallengeAndIssuesToken() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        stubSuccessfulConsume();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        LoginAuthenticate result = service.verify(CHALLENGE_ID, TENANT_ID, CODE);

        assertEquals(ISSUED, result);
        verify(challengeCache).deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN);
        verify(challengeCache, never()).incrementAttempts(any(), any());
        verify(challengeCache, never()).delete(any(), any());
        verify(securityTokenService).generateTokenAndRefreshToken(any());
        verify(tokenCacheService, never()).addToBlacklist(any(), anyString(), any());
    }

    @Test
    void when_verifyWithInvalidCode_thenIncrementsAttemptsAndReturnsInvalidCode() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_CODE, ex.code());
        verify(challengeCache).incrementAttempts(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).delete(any(), any());
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithFourPriorAttemptsAndValidCode_thenFifthAttemptStillSucceeds() {
        stubChallenge(4, 120);
        stubEnabledSecret();
        stubMatchingContext();
        stubSuccessfulConsume();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        LoginAuthenticate result = service.verify(CHALLENGE_ID, TENANT_ID, CODE);

        assertEquals(ISSUED, result);
        verify(mfaPort).verify(SECRET, CODE);
        verify(challengeCache).deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN);
        verify(challengeCache, never()).incrementAttempts(any(), any());
    }

    @Test
    void when_verifyAfterFifthInvalidAttempt_thenTooManyAttemptsChallengeDeletedAndUserBlocked() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID))
                .thenReturn(MfaChallengeCachePort.MAX_ATTEMPTS);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS, ex.code());
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache).blockUser(TENANT_ID, USER_ID);
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithAlreadyMaxedAttempts_thenTooManyAttemptsWithoutVerifying() {
        stubChallenge(MfaChallengeCachePort.MAX_ATTEMPTS, 120);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS, ex.code());
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(mfaPort, never()).verify(anyString(), anyString());
    }

    @Test
    void when_verifyWithExpiredChallenge_thenChallengeExpiredAndDeleted() {
        stubChallenge(0, -1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(mfaPort, never()).verify(anyString(), anyString());
    }

    @Test
    void when_verifyWithMissingChallenge_thenChallengeExpired() {
        when(challengeCache.get(TENANT_ID, CHALLENGE_ID)).thenReturn(Optional.empty());

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verify(challengeCache, never()).delete(any(), any());
    }

    @Test
    void when_verifyErrorMessages_thenNoSecretOrCodeLeaked() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertFalse(ex.getMessage().contains(SECRET));
        assertFalse(ex.getMessage().contains(CODE));
    }

    @Test
    void when_verifyRuns_thenNoSecretOrCodeWrittenToLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(MfaService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            stubChallenge(0, 120);
            stubEnabledSecret();
            stubMatchingContext();
            when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
            when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

            assertThrows(MfaException.class, () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.joining(" "));
            assertFalse(logs.contains(SECRET));
            assertFalse(logs.contains(CODE));
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ---------- recovery ----------

    @Test
    void when_recoveryWithValidUnusedCode_thenMarksUsedConsumesChallengeAndIssuesToken() {
        stubChallenge(0, 120);
        stubEnabledOnly();
        stubMatchingContext();
        stubSuccessfulConsume();
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of(
                new MfaRecoveryCodeData(RECOVERY_ID, USER_ID, RECOVERY_HASH, null, Instant.now())));
        when(repository.markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class))).thenReturn(1);

        LoginAuthenticate result = service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE);

        assertEquals(ISSUED, result);
        verify(repository).markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class));
        verify(challengeCache).deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN);
        verify(challengeCache, never()).incrementAttempts(any(), any());
        verify(securityTokenService).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_recoveryWithAlreadyUsedCode_thenInvalidRecoveryAndIncrementsAttempts() {
        stubChallenge(0, 120);
        stubEnabledOnly();
        stubMatchingContext();
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of(
                new MfaRecoveryCodeData(RECOVERY_ID, USER_ID, RECOVERY_HASH,
                        Instant.now().minus(1, ChronoUnit.DAYS), Instant.now())));
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_RECOVERY, ex.code());
        verify(repository, never()).markRecoveryCodeUsedIfUnused(any(), any());
        verify(challengeCache).incrementAttempts(TENANT_ID, CHALLENGE_ID);
    }

    @Test
    void when_recoveryWithUnknownCode_thenInvalidRecovery() {
        stubChallenge(0, 120);
        stubEnabledOnly();
        stubMatchingContext();
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of());
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_RECOVERY, ex.code());
        verify(challengeCache, never()).delete(any(), any());
    }

    @Test
    void when_recoveryUpdateAffectsZeroRows_thenInvalidRecoveryAndIncrementsAttempts() {
        // Cenário sequencial do "perdeu a disputa": outro chamador já consumiu o código.
        stubChallenge(0, 120);
        stubEnabledOnly();
        stubMatchingContext();
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of(
                new MfaRecoveryCodeData(RECOVERY_ID, USER_ID, RECOVERY_HASH, null, Instant.now())));
        when(repository.markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class))).thenReturn(0);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_RECOVERY, ex.code());
        verify(challengeCache).incrementAttempts(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).delete(any(), any());
    }

    @Test
    void when_twoChallengesUseSameRecoveryCodeConcurrently_thenOnlyOneSucceedsAndOtherGetsInvalidRecovery()
            throws Exception {
        UUID secondChallengeId = UUID.randomUUID();
        stubChallenge(0, 120);
        when(challengeCache.get(TENANT_ID, secondChallengeId)).thenReturn(Optional.of(
                challenge(secondChallengeId, 0, 120, CONSUME_TOKEN, null)));
        stubEnabledOnly();
        stubMatchingContext();
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        when(userService.findById(USER_ID)).thenReturn(user);
        when(securityTokenService.generateTokenAndRefreshToken(any())).thenReturn(ISSUED);
        AtomicReference<Boolean> challengeConsumed = new AtomicReference<>(Boolean.FALSE);
        when(challengeCache.deleteIfTokenMatches(eq(TENANT_ID), any(UUID.class), eq(CONSUME_TOKEN)))
                .thenAnswer(inv -> challengeConsumed.compareAndSet(Boolean.FALSE, Boolean.TRUE));
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        // As duas requisições leem o código como não usado antes de qualquer escrita.
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of(
                new MfaRecoveryCodeData(RECOVERY_ID, USER_ID, RECOVERY_HASH, null, Instant.now())));
        // Simula o UPDATE condicional atômico do banco: só o primeiro compareAndSet afeta 1 linha.
        AtomicReference<Boolean> unused = new AtomicReference<>(Boolean.TRUE);
        when(repository.markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class)))
                .thenAnswer(inv -> unused.compareAndSet(Boolean.TRUE, Boolean.FALSE) ? 1 : 0);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> first = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE);
                return "first";
            };
            Callable<String> second = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                service.recovery(secondChallengeId, TENANT_ID, RECOVERY_CODE);
                return "second";
            };
            Future<String> firstResult = executor.submit(first);
            Future<String> secondResult = executor.submit(second);

            int successes = 0;
            int invalidRecovery = 0;
            for (Future<String> result : List.of(firstResult, secondResult)) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    assertTrue(cause instanceof MfaException, "causa inesperada: " + cause);
                    assertEquals(MfaErrorCode.MFA_INVALID_RECOVERY, ((MfaException) cause).code());
                    invalidRecovery++;
                }
            }

            assertEquals(1, successes, "apenas um challenge deve consumir o código");
            assertEquals(1, invalidRecovery, "o outro challenge deve receber MFA_INVALID_RECOVERY");
            verify(repository, times(2)).markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class));
            verify(challengeCache, times(1)).deleteIfTokenMatches(eq(TENANT_ID), any(UUID.class), eq(CONSUME_TOKEN));
            verify(challengeCache, times(1)).incrementAttempts(eq(TENANT_ID), any());
            verify(securityTokenService, times(1)).generateTokenAndRefreshToken(any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void when_recoveryWithExpiredChallenge_thenChallengeExpiredBeforeHashing() {
        stubChallenge(0, -1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verify(cryptoPort, never()).hashRecoveryCode(anyString());
        assertTrue(ex.getMessage() != null && !ex.getMessage().contains(RECOVERY_CODE));
    }

    @Test
    void when_verifyWithDivergentRemoteIp_then401AndNoToken() {
        stubChallenge(0, 120);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.9");
        when(request.getHeader("User-Agent")).thenReturn("it-agent");

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verify(mfaPort, never()).verify(anyString(), anyString());
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithDivergentUserAgent_then401AndNoToken() {
        stubChallenge(0, 120);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("other-agent");

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithAlreadyConsumedChallenge_then401AndNoToken() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);
        when(challengeCache.deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN)).thenReturn(false);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ex.code());
        verify(userService, never()).findById(any());
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithOldJti_thenBlacklistsBeforeIssuingToken() {
        UUID freshChallengeId = UUID.randomUUID();
        when(challengeCache.get(TENANT_ID, freshChallengeId)).thenReturn(Optional.of(
                challenge(freshChallengeId, 0, 120, CONSUME_TOKEN, OLD_JTI)));
        stubEnabledSecret();
        stubMatchingContext();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        when(userService.findById(USER_ID)).thenReturn(user);
        when(challengeCache.deleteIfTokenMatches(TENANT_ID, freshChallengeId, CONSUME_TOKEN)).thenReturn(true);
        when(securityTokenService.generateTokenAndRefreshToken(any())).thenReturn(ISSUED);

        assertEquals(ISSUED, service.verify(freshChallengeId, TENANT_ID, CODE));

        InOrder order = inOrder(challengeCache, tokenCacheService, securityTokenService);
        order.verify(challengeCache).deleteIfTokenMatches(TENANT_ID, freshChallengeId, CONSUME_TOKEN);
        order.verify(tokenCacheService).addToBlacklist(eq(TENANT_ID), eq(OLD_JTI), eq(Duration.ofMinutes(15)));
        order.verify(securityTokenService).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_fifthAttemptBlocksUser_thenFreshChallengeGets429WithoutValidatingCode() {
        when(challengeCache.isUserBlocked(TENANT_ID, USER_ID)).thenReturn(true);
        UUID freshChallengeId = UUID.randomUUID();
        when(challengeCache.get(TENANT_ID, freshChallengeId)).thenReturn(Optional.of(
                challenge(freshChallengeId, 0, 120, CONSUME_TOKEN, null)));

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(freshChallengeId, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS, ex.code());
        verify(mfaPort, never()).verify(anyString(), anyString());
        verifyNoTokenIssued();
    }

    @Test
    void when_twoConcurrentVerifiesWithSameChallenge_thenOnlyOneTokenIsIssued() throws Exception {
        stubChallenge(0, 120);
        stubEnabledSecret();
        stubMatchingContext();
        stubSuccessfulConsume();
        AtomicReference<Boolean> consumed = new AtomicReference<>(Boolean.FALSE);
        when(challengeCache.deleteIfTokenMatches(TENANT_ID, CHALLENGE_ID, CONSUME_TOKEN))
                .thenAnswer(inv -> consumed.compareAndSet(Boolean.FALSE, Boolean.TRUE));
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<LoginAuthenticate> first = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service.verify(CHALLENGE_ID, TENANT_ID, CODE);
            };
            Callable<LoginAuthenticate> second = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service.verify(CHALLENGE_ID, TENANT_ID, CODE);
            };
            Future<LoginAuthenticate> one = executor.submit(first);
            Future<LoginAuthenticate> two = executor.submit(second);

            int successes = 0;
            int expired = 0;
            for (Future<LoginAuthenticate> future : List.of(one, two)) {
                try {
                    assertEquals(ISSUED, future.get(10, TimeUnit.SECONDS));
                    successes++;
                } catch (ExecutionException e) {
                    assertTrue(e.getCause() instanceof MfaException, "causa inesperada: " + e.getCause());
                    assertEquals(MfaErrorCode.MFA_CHALLENGE_EXPIRED, ((MfaException) e.getCause()).code());
                    expired++;
                }
            }
            assertEquals(1, successes, "desafio não pode ser reutilizado");
            assertEquals(1, expired);
            verify(securityTokenService, times(1)).generateTokenAndRefreshToken(any());
        } finally {
            executor.shutdownNow();
        }
    }

    private void verifyNoTokenIssued() {
        verifyNoInteractions(securityTokenService);
    }
}
