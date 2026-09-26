package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.api.exceptions.KbInvalidUrlException;

import java.util.Locale;
import java.util.Set;

public final class KbFileValidator {

    public static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    public static final int MAX_TEXT_CHARS = 200_000;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/markdown", "text/html",
            "application/x-pdf", "application/octet-stream");

    private KbFileValidator() {
    }

    public static void checkSize(long size) {
        if (size > MAX_FILE_BYTES) {
            throw new br.com.systec.hive.knowledge.api.exceptions.KbFileTooLargeException();
        }
    }

    public static void checkUpload(String filename, String contentType, byte[] bytes) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean knownExt = name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".md")
                || name.endsWith(".markdown") || name.endsWith(".html") || name.endsWith(".htm");
        if (!knownExt) {
            throw new KbInvalidUrlException("unsupported file type: " + filename);
        }
        if (bytes != null && bytes.length >= 4) {
            if (bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
                throw new KbInvalidUrlException("binary executable blocked (magic-bytes)");
            }
            if (bytes[0] == 'M' && bytes[1] == 'Z') {
                throw new KbInvalidUrlException("binary executable blocked (magic-bytes)");
            }
        }
        if (contentType != null && !contentType.isBlank()) {
            String ct = contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
            if (!ALLOWED_CONTENT_TYPES.contains(ct) && !ct.startsWith("text/")) {
                throw new KbInvalidUrlException("content-type not allowed: " + contentType);
            }
        }
    }

    public static String truncateText(String text) {
        if (text != null && text.length() > MAX_TEXT_CHARS) {
            return text.substring(0, MAX_TEXT_CHARS);
        }
        return text;
    }
}
