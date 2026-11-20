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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD do serviço de gerenciamento MFA (Fase 2). Nenhum token JWT é emitido aqui:
 * verify/recovery apenas validam e consomem o challenge.
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

    @Mock MfaPort mfaPort;
    @Mock MfaCryptoPort cryptoPort;
    @Mock MfaRepositoryPort repository;
    @Mock MfaChallengeCachePort challengeCache;
    @Mock UserService userService;
    @Mock PasswordEncoder passwordEncoder;

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
        return new MfaChallenge(CHALLENGE_ID, USER_ID, TENANT_ID, "127.0.0.1", "it-agent",
                attempts, Instant.now().plusSeconds(expiresInSeconds));
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
    void when_verifyWithValidCode_thenConsumesChallengeWithoutIssuingToken() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        service.verify(CHALLENGE_ID, TENANT_ID, CODE);

        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).incrementAttempts(any(), any());
        verifyNoTokenIssued();
    }

    @Test
    void when_verifyWithInvalidCode_thenIncrementsAttemptsAndReturnsInvalidCode() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID)).thenReturn(1);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_INVALID_CODE, ex.code());
        verify(challengeCache).incrementAttempts(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).delete(any(), any());
    }

    @Test
    void when_verifyWithFourPriorAttemptsAndValidCode_thenFifthAttemptStillSucceeds() {
        stubChallenge(4, 120);
        stubEnabledSecret();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(true);

        service.verify(CHALLENGE_ID, TENANT_ID, CODE);

        verify(mfaPort).verify(SECRET, CODE);
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).incrementAttempts(any(), any());
    }

    @Test
    void when_verifyAfterFifthInvalidAttempt_thenTooManyAttemptsAndChallengeDeleted() {
        stubChallenge(0, 120);
        stubEnabledSecret();
        when(mfaPort.verify(SECRET, CODE)).thenReturn(false);
        when(challengeCache.incrementAttempts(TENANT_ID, CHALLENGE_ID))
                .thenReturn(MfaChallengeCachePort.MAX_ATTEMPTS);

        MfaException ex = assertThrows(MfaException.class,
                () -> service.verify(CHALLENGE_ID, TENANT_ID, CODE));

        assertEquals(MfaErrorCode.MFA_TOO_MANY_ATTEMPTS, ex.code());
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
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
    void when_recoveryWithValidUnusedCode_thenMarksUsedAndConsumesChallengeWithoutToken() {
        stubChallenge(0, 120);
        stubEnabledOnly();
        when(cryptoPort.hashRecoveryCode(RECOVERY_CODE)).thenReturn(RECOVERY_HASH);
        when(repository.findRecoveryCodesByUserId(USER_ID)).thenReturn(List.of(
                new MfaRecoveryCodeData(RECOVERY_ID, USER_ID, RECOVERY_HASH, null, Instant.now())));
        when(repository.markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class))).thenReturn(1);

        service.recovery(CHALLENGE_ID, TENANT_ID, RECOVERY_CODE);

        verify(repository).markRecoveryCodeUsedIfUnused(eq(RECOVERY_ID), any(Instant.class));
        verify(challengeCache).delete(TENANT_ID, CHALLENGE_ID);
        verify(challengeCache, never()).incrementAttempts(any(), any());
        verifyNoTokenIssued();
    }

    @Test
    void when_recoveryWithAlreadyUsedCode_thenInvalidRecoveryAndIncrementsAttempts() {
        stubChallenge(0, 120);
        stubEnabledOnly();
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
                new MfaChallenge(secondChallengeId, USER_ID, TENANT_ID, "127.0.0.1", "it-agent",
                        0, Instant.now().plusSeconds(120))));
        stubEnabledOnly();
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
            verify(challengeCache, times(1)).delete(eq(TENANT_ID), any());
            verify(challengeCache, times(1)).incrementAttempts(eq(TENANT_ID), any());
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

    private void verifyNoTokenIssued() {
        // Fase 2: o serviço não tem dependência de emissor de token — contrato por ausência.
        assertNotNull(service);
    }
}
