package br.com.systec.hive.security.impl.security;

import br.com.systec.hive.security.api.service.MfaPort;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * Adaptador TOTP — única classe que conhece a lib {@code dev.samstevens.totp:1.7.1}.
 *
 * <p><strong>Parâmetros explícitos</strong> (não defaults da lib): período de 30 s e
 * discrepância de 1 período (±30 s). O {@link TimeProvider} é injetável para testes
 * determinísticos; em produção o bean usa {@link SystemTimeProvider}.
 *
 * <p><strong>Sem logs:</strong> secret, código e QR nunca são escritos em log. Exceções de
 * geração de QR são embrulhadas com mensagem genérica, sem conter o secret.
 */
@Service
public class TotpAdapter implements MfaPort {

    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_DISCREPANCY = 1;
    private static final int TOTP_DIGITS = 6;
    private static final String ISSUER_DEFAULT = "Hive";

    private final TimeProvider timeProvider;
    private final DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator();

    /** Construtor usado pelo Spring: relógio do sistema. */
    @Autowired
    public TotpAdapter() {
        this(new SystemTimeProvider());
    }

    /** Construtor para teste determinístico. */
    public TotpAdapter(TimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String qrDataUri(String secret, String userLabel, String issuer) {
        ZxingPngQrGenerator generator = new ZxingPngQrGenerator();
        QrData data = new QrData.Builder()
                .label(userLabel)
                .secret(secret)
                .issuer(issuer == null || issuer.isBlank() ? ISSUER_DEFAULT : issuer)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(TOTP_DIGITS)
                .period(TIME_PERIOD_SECONDS)
                .build();
        try {
            byte[] png = generator.generate(data);
            return "data:" + generator.getImageMimeType() + ";base64,"
                    + Base64.getEncoder().encodeToString(png);
        } catch (QrGenerationException e) {
            // Mensagem sem secret, label ou dados do QR.
            throw new IllegalStateException("Falha ao gerar o QR code de configuração do MFA.", e);
        }
    }

    @Override
    public boolean verify(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        DefaultCodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);
        verifier.setTimePeriod(TIME_PERIOD_SECONDS);
        verifier.setAllowedTimePeriodDiscrepancy(ALLOWED_DISCREPANCY);
        return verifier.isValidCode(secret, code);
    }
}
