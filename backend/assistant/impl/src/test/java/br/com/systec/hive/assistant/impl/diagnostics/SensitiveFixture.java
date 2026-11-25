package br.com.systec.hive.assistant.impl.diagnostics;

import java.util.List;

/** Literais sensíveis usados somente em teste — nunca em log, payload ou mensagem de erro. */
public final class SensitiveFixture {

    public static final String RAW = "CPF 123.456.789-09; CNPJ 12.345.678/0001-90; "
            + "CNPJ 12.ABC.345/01DE-35; email joao.silva@example.com; fone (11) 98765-4321; "
            + "token sk-abcdef1234567890; senha=SuperSecret123";

    public static final String REDACTED = "CPF [REDACTED:cpf]; CNPJ [REDACTED:cnpj]; "
            + "CNPJ [REDACTED:cnpj]; email [REDACTED:email]; fone [REDACTED:phone]; "
            + "token [REDACTED:secret]; senha [REDACTED:secret]";

    public static final List<String> LITERALS = List.of(
            "123.456.789-09", "12.345.678/0001-90", "12.ABC.345/01DE-35",
            "joao.silva@example.com", "(11) 98765-4321", "sk-abcdef1234567890", "SuperSecret123");

    public static final List<String> PLACEHOLDERS = List.of(
            "[REDACTED:cpf]", "[REDACTED:cnpj]", "[REDACTED:email]",
            "[REDACTED:phone]", "[REDACTED:secret]");

    private SensitiveFixture() {
    }
}
