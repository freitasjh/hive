package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-09: 1 incremento por evento fail-closed, nome congelado, sem rótulo com texto. */
class RedactionFailureMetricTest {

    @Test
    void when_oneRedactionFails_then_failureCounterIncrementsOnce() {
        TextRedactor redactor = new TextRedactor(text -> {
            throw new IllegalStateException("forced");
        });

        TextCarrier.start();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);

        assertThat(redactor.failureCount()).isEqualTo(1);
        assertThat(TextRedactor.FAILURE_METRIC).isEqualTo("chat.telemetry.redaction.failures");
    }

    @Test
    void when_redactionFails_then_micrometerCallbackRunsOncePerFailClosedEvent() {
        int[] callbacks = {0};
        TextRedactor redactor = new TextRedactor(text -> {
            throw new IllegalStateException("forced");
        }, () -> callbacks[0]++);

        TextCarrier.start();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);

        assertThat(callbacks[0]).isEqualTo(1);
    }

    @Test
    void when_twoAttemptsFail_then_counterIsTwoAndSuccessDoesNotIncrement() {
        TextRedactor redactor = new TextRedactor(text -> {
            throw new IllegalStateException("forced");
        });
        TextCarrier.start();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);
        TextCarrier.start();
        redactor.captureInput(SensitiveFixture.RAW);
        redactor.captureOutput(SensitiveFixture.RAW);

        assertThat(redactor.failureCount()).isEqualTo(2);

        TextRedactor healthy = new TextRedactor();
        TextCarrier.start();
        healthy.captureInput(SensitiveFixture.RAW);
        healthy.captureOutput(SensitiveFixture.RAW);
        assertThat(healthy.failureCount()).isZero();
    }

    @Test
    void when_metricNameIsRead_then_itHasNoTextLabel() {
        assertThat(TextRedactor.FAILURE_METRIC).doesNotContain(SensitiveFixture.LITERALS.toArray(String[]::new));
        assertThat(TextRedactor.FAILURE_METRIC).doesNotContain("{", "=", "sk-");
    }
}
