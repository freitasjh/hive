package br.com.systec.hive.assistant.api.service;

/** Public quota constants (CR-04): the web layer derives headers from the same numbers as the limiter. */
public final class ChatRateLimits {

    public static final int MAX_CALLS = 10;
    public static final long WINDOW_SECONDS = 60L;

    private ChatRateLimits() {
    }
}
