package br.com.systec.hive.assistant.impl.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-05/RED-15: placeholder exato e conjunto fechado de regras. */
class PlaceholderContractTest {

    @Test
    void when_rulesAreEnumerated_then_onlyTheFiveFrozenPlaceholdersExist() {
        List<String> placeholders = Arrays.stream(RedactionPolicy.Rule.values())
                .map(RedactionPolicy.Rule::placeholder)
                .toList();

        assertThat(RedactionPolicy.Rule.values()).extracting(Enum::name)
                .containsExactly("CPF", "CNPJ", "EMAIL", "PHONE", "SECRET");
        assertThat(placeholders).containsExactly(
                "[REDACTED:cpf]", "[REDACTED:cnpj]", "[REDACTED:email]",
                "[REDACTED:phone]", "[REDACTED:secret]");
    }

    @Test
    void when_fixtureIsRedacted_then_onlyClosedPlaceholdersAppear() {
        String redacted = RedactionPolicy.redact(SensitiveFixture.RAW);
        assertThat(redacted).contains(SensitiveFixture.PLACEHOLDERS.toArray(String[]::new));
        assertThat(redacted.replaceAll("\\[REDACTED:(cpf|cnpj|email|phone|secret)]", ""))
                .isEqualTo("CPF ; CNPJ ; CNPJ ; email ; fone ; token ; senha ");
    }

    @Test
    void when_attributeKeysAreRead_then_textKeysAreExactlyTheFrozenOnes() {
        assertThat(SpanAttributeMapper.KEY_INPUT).isEqualTo("langfuse.observation.input");
        assertThat(SpanAttributeMapper.KEY_OUTPUT).isEqualTo("langfuse.observation.output");
    }
}
