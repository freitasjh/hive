package br.com.systec.hive.security.impl;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 0 do plano MFA: prova de viabilidade do build — a lib 1.7.1 compila e
 * gera/verifica codigo em Java 25. Nao e dominio MFA (sem VO/service/endpoint).
 */
class TotpLibrarySmokeTest {

    // DefaultCodeVerifier default: timePeriod = 30s (confirmed in 1.7.1 bytecode).
    private static final long TIME_PERIOD_SECONDS = 30L;

    private static long currentTimeStep() {
        // generate(String,long) takes the time-step counter, NOT raw epoch seconds:
        // generateHash uses the long directly as HMAC counter; the verifier calls
        // Math.floorDiv(timeProvider.getTime(), timePeriod) before generating.
        return Math.floorDiv(new SystemTimeProvider().getTime(), TIME_PERIOD_SECONDS);
    }

    @Test
    void when_generatedCodeIsVerified_then_valid() throws CodeGenerationException {
        String secret = new DefaultSecretGenerator().generate();
        String code = new DefaultCodeGenerator().generate(secret, currentTimeStep());
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(), new SystemTimeProvider());

        assertTrue(verifier.isValidCode(secret, code));
    }

    @Test
    void when_differentCodeIsVerified_then_invalid() throws CodeGenerationException {
        String secret = new DefaultSecretGenerator().generate();
        String code = new DefaultCodeGenerator().generate(secret, currentTimeStep());
        String wrongCode = code.charAt(0) == '0'
                ? "1" + code.substring(1)
                : "0" + code.substring(1);
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(), new SystemTimeProvider());

        assertFalse(verifier.isValidCode(secret, wrongCode));
    }
}
