package br.com.systec.hive.assistant.impl.diagnostics;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Política fechada de redação (ADR-003, emenda RED-01): só JDK, sem dependência nova.
 *
 * <p>{@link Pattern} é imutável e thread-safe; {@link Matcher} é por chamada. A ordem importa:
 * segredos primeiro para que rótulos como {@code senha=} continuem legíveis e o valor desapareça.
 */
public final class RedactionPolicy {

    public enum Rule {
        CPF("cpf"), CNPJ("cnpj"), EMAIL("email"), PHONE("phone"), SECRET("secret");

        private final String token;

        Rule(String token) {
            this.token = token;
        }

        public String placeholder() {
            return "[REDACTED:" + token + "]";
        }
    }

    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\"?\\b(?:senha|password|passwd|pwd|secret|token|api[_-]?key|apikey|key)\"?)\\s*[:=]\\s*\"?[^\\s;\"']+\"?");
    private static final Pattern SK_TOKEN = Pattern.compile("\\bsk-[A-Za-z0-9]{4,}\\b");
    private static final Pattern CPF = Pattern.compile(
            "(?<![\\d.])\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}(?!\\d)|(?<!\\d)\\d{11}(?!\\d)");
    private static final Pattern CNPJ_FORMATTED = Pattern.compile(
            "(?<![\\w.])(?:\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}"
                    + "|[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-\\d{2})(?![\\w.])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CNPJ_BARE = Pattern.compile("(?<!\\d)\\d{14}(?!\\d)");
    private static final Pattern CNPJ_BARE_ALNUM = Pattern.compile(
            "(?<![A-Z0-9])[A-Z0-9]{12}\\d{2}(?![A-Z0-9])", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile(
            "\\(\\d{2}\\)\\s*\\d{4,5}-\\d{4}|(?<!\\d)\\d{2}\\s\\d{4,5}-\\d{4}(?!\\d)");
    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private RedactionPolicy() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = replace(SECRET_ASSIGNMENT, text,
                matched -> secretMatched(matched) + " " + Rule.SECRET.placeholder());
        out = replace(SK_TOKEN, out, matched -> Rule.SECRET.placeholder());
        out = replace(CPF, out, matched -> validCpf(matched) ? Rule.CPF.placeholder() : matched);
        // shortcut: CNPJ com pontuação é redigido por shape, sem exigir DV — a fixture canônica do
        //   ADR-003 (12.345.678/0001-90) tem DV inválido; redigir é fail-safe para RF3.
        out = replace(CNPJ_FORMATTED, out, matched -> Rule.CNPJ.placeholder());
        out = replace(CNPJ_BARE, out, matched -> validCnpj(matched) ? Rule.CNPJ.placeholder() : matched);
        out = replace(CNPJ_BARE_ALNUM, out,
                matched -> validCnpjAlnum(matched) ? Rule.CNPJ.placeholder() : matched);
        out = replace(EMAIL, out, matched -> Rule.EMAIL.placeholder());
        return replace(PHONE, out, matched -> Rule.PHONE.placeholder());
    }

    private static String secretMatched(String matched) {
        Matcher matcher = SECRET_ASSIGNMENT.matcher(matched);
        return matcher.find() ? matcher.group(1).replace("\"", "") : "";
    }

    private static String replace(Pattern pattern, String text, Function<String, String> replacement) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        boolean any = false;
        while (matcher.find()) {
            any = true;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.apply(matcher.group())));
        }
        if (!any) {
            return text;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean validCpf(String matched) {
        String digits = matched.replaceAll("\\D", "");
        if (digits.length() != 11) {
            return false;
        }
        return checkDigit(digits, 9, 10) == Character.digit(digits.charAt(9), 10)
                && checkDigit(digits, 10, 11) == Character.digit(digits.charAt(10), 10);
    }

    private static int checkDigit(String digits, int count, int startWeight) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += Character.digit(digits.charAt(i), 10) * (startWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean validCnpj(String matched) {
        if (matched.length() != 14) {
            return false;
        }
        int first = dv(matched, CNPJ_WEIGHTS_1);
        int second = dv(matched.substring(0, 12) + first, CNPJ_WEIGHTS_2);
        return first == Character.digit(matched.charAt(12), 10)
                && second == Character.digit(matched.charAt(13), 10);
    }

    /** Mesmo módulo 11, mas com letras normalizadas — CNPJ alfanumérico nu em qualquer caixa. */
    private static boolean validCnpjAlnum(String matched) {
        if (matched.length() != 14) {
            return false;
        }
        String upper = matched.toUpperCase(Locale.ROOT);
        int first = dv(upper, CNPJ_WEIGHTS_1);
        int second = dv(upper.substring(0, 12) + first, CNPJ_WEIGHTS_2);
        return first == Character.digit(upper.charAt(12), 10)
                && second == Character.digit(upper.charAt(13), 10);
    }

    /** Módulo 11 com valor ASCII−48, conforme o manual do DV do CNPJ alfanumérico. */
    private static int dv(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (base.charAt(i) - 48) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
