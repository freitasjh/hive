package br.com.systec.hive.assistant.impl.util;

import br.com.systec.hive.assistant.api.exceptions.InvalidTemplateVariableException;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptVariableExtractor {

    private PromptVariableExtractor() {
    }

    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}");
    private static final Pattern ANY_BRACES_PATTERN = Pattern.compile("\\{\\{[^}]*\\}\\}");

    public static Set<String> extractParams(String template) {
        if (template == null || template.isBlank()) {
            return Collections.emptySet();
        }
        validateTemplate(template);
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        Set<String> params = new LinkedHashSet<>();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            params.add(varName);
        }
        return Collections.unmodifiableSet(params);
    }

    public static void validateTemplate(String template) {
        if (template == null || !template.contains("{{")) {
            return;
        }
        int index = 0;
        while ((index = template.indexOf("{{", index)) != -1) {
            int close = template.indexOf("}}", index + 2);
            if (close == -1) {
                String snippet = template.substring(index, Math.min(index + 20, template.length()));
                throw new InvalidTemplateVariableException(snippet, index);
            }
            String token = template.substring(index, close + 2);
            Matcher validMatcher = VARIABLE_PATTERN.matcher(token);
            if (!validMatcher.matches()) {
                throw new InvalidTemplateVariableException(token, index);
            }
            index = close + 2;
        }
    }
}
