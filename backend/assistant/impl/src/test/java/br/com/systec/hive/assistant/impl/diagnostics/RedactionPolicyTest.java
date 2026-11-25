package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-03/RED-04: CPF, CNPJ numérico e alfanumérico, e-mail, telefone, `sk-` e senha. */
class RedactionPolicyTest {

    @Test
    void when_sensitiveFixtureIsRedacted_then_everyLiteralIsReplacedByItsClosedRulePlaceholder() {
        String redacted = RedactionPolicy.redact(SensitiveFixture.RAW);

        assertThat(redacted).isEqualTo(SensitiveFixture.REDACTED);
        for (String literal : SensitiveFixture.LITERALS) {
            assertThat(redacted).doesNotContain(literal);
        }
        assertThat(redacted).doesNotContain("sk-");
    }

    @Test
    void when_bothCnpjFormatsAppear_then_bothAreRedacted() {
        assertThat(RedactionPolicy.redact("CNPJ 12.345.678/0001-90"))
                .isEqualTo("CNPJ [REDACTED:cnpj]");
        assertThat(RedactionPolicy.redact("CNPJ 12.ABC.345/01DE-35"))
                .isEqualTo("CNPJ [REDACTED:cnpj]");
    }

    @Test
    void when_cnpjIsBareOrLowercase_then_bothNumericAndAlphanumericAreStillRedacted() {
        assertThat(RedactionPolicy.redact("CNPJ 12abc34501de35"))
                .isEqualTo("CNPJ [REDACTED:cnpj]");
        assertThat(RedactionPolicy.redact("CNPJ 12.abc.345/01de-35"))
                .isEqualTo("CNPJ [REDACTED:cnpj]");
        assertThat(RedactionPolicy.redact("CNPJ 12345678000195"))
                .isEqualTo("CNPJ [REDACTED:cnpj]");
    }

    @Test
    void when_bareCnpjHasInvalidCheckDigits_then_itIsNotRedacted() {
        assertThat(RedactionPolicy.redact("CNPJ 12abc34501de36"))
                .isEqualTo("CNPJ 12abc34501de36");
        assertThat(RedactionPolicy.redact("CNPJ 12345678000190"))
                .isEqualTo("CNPJ 12345678000190");
    }

    @Test
    void when_secretIsAQuotedJsonKey_then_valueIsRedacted() {
        assertThat(RedactionPolicy.redact("{\"password\":\"SuperSecret123\"}"))
                .isEqualTo("{password [REDACTED:secret]}");
        assertThat(RedactionPolicy.redact("\"senha\" : \"SuperSecret123\""))
                .isEqualTo("senha [REDACTED:secret]");
    }

    @Test
    void when_cpfHasOnlyDigits_then_itIsRedactedToo() {
        assertThat(RedactionPolicy.redact("CPF 12345678909")).isEqualTo("CPF [REDACTED:cpf]");
    }

    @Test
    void when_secretHasNoKnownPrefix_then_entropyFallbackIsNotApplied() {
        assertThat(RedactionPolicy.redact("token aleatorio sem prefixo")).isEqualTo("token aleatorio sem prefixo");
    }

    @Test
    void when_textIsNullOrBlank_then_nothingIsInvented() {
        assertThat(RedactionPolicy.redact(null)).isNull();
        assertThat(RedactionPolicy.redact("")).isEmpty();
    }
}
