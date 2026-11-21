package br.com.systec.hive.assistant.impl.util;

public final class ChatTitleUtil {

    private ChatTitleUtil() {
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "Nova conversa";
        }
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return "Nova conversa";
        }
        int codePointCount = trimmed.codePointCount(0, trimmed.length());
        if (codePointCount <= max) {
            return trimmed;
        }
        int endIndex = trimmed.offsetByCodePoints(0, max);
        return trimmed.substring(0, endIndex);
    }
}
