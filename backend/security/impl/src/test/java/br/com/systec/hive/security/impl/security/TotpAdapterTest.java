package br.com.systec.hive.security.impl.security;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD do adaptador TOTP. Tempo fixo injetado: nenhum teste depende de relógio real.
 * Nenhum secret/código é escrito em log — o adaptador não possui logger.
 */
class TotpAdapterTest {

    private static final long FIXED_EPOCH_SECONDS = 1_700_000_000L;
    private static final long TIME_PERIOD_SECONDS = 30L;
    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private TotpAdapter adapter;

    @BeforeEach
    void setUp() {
        TimeProvider fixedClock = () -> FIXED_EPOCH_SECONDS;
        adapter = new TotpAdapter(fixedClock);
    }

    private static String codeAt(String secret, long timeStep) throws CodeGenerationException {
        // generate(String,long) recebe o CONTADOR de período, não epoch seconds crus (RESEARCH §5).
        return new DefaultCodeGenerator().generate(secret, timeStep);
    }

    private static long currentTimeStep() {
        return Math.floorDiv(FIXED_EPOCH_SECONDS, TIME_PERIOD_SECONDS);
    }

    @Test
    void when_generateSecret_then_base32SecretOf32Chars() {
        String secret = adapter.generateSecret();

        assertNotNull(secret);
        assertTrue(secret.matches("[A-Z2-7]{32}"));
    }

    @Test
    void when_qrDataUri_then_pngDataUriIsGenerated() {
        String secret = adapter.generateSecret();

        String uri = adapter.qrDataUri(secret, "user-123", "Hive");

        assertTrue(uri.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length()));
        assertArrayEquals(PNG_MAGIC, java.util.Arrays.copyOf(png, PNG_MAGIC.length));
    }

    @Test
    void when_validCodeForCurrentPeriod_then_verified() throws CodeGenerationException {
        String secret = adapter.generateSecret();
        String code = codeAt(secret, currentTimeStep());

        assertTrue(adapter.verify(secret, code));
    }

    @Test
    void when_wrongCode_then_notVerified() throws CodeGenerationException {
        String secret = adapter.generateSecret();
        String code = codeAt(secret, currentTimeStep());
        String wrong = code.charAt(0) == '0' ? "1" + code.substring(1) : "0" + code.substring(1);

        assertFalse(adapter.verify(secret, wrong));
    }

    @Test
    void when_codeFromPreviousPeriod_then_verifiedWithinDiscrepancy() throws CodeGenerationException {
        String secret = adapter.generateSecret();
        String previousCode = codeAt(secret, currentTimeStep() - 1);

        // setAllowedTimePeriodDiscrepancy(1) explícito: ±1 período deve ser aceito.
        assertTrue(adapter.verify(secret, previousCode));
    }

    @Test
    void when_blankInputs_then_notVerifiedWithoutException() {
        assertFalse(adapter.verify(null, "123456"));
        assertFalse(adapter.verify("JBSWY3DPEHPK3PXP", null));
        assertFalse(adapter.verify("JBSWY3DPEHPK3PXP", "  "));
    }
}
