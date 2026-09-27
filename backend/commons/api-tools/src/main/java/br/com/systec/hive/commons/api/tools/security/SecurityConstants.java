package br.com.systec.hive.commons.api.tools.security;

/**
 * The permissions a token can carry.
 *
 * <p>The two knowledge permissions used to live here. Nothing checked them since
 * `KnowledgeBaseControllerV1` was deleted with the knowledge module, but `DEFAULT_PERMISSIONS` kept
 * granting both — so every token shipped two permissions that unlocked nothing, and anyone auditing
 * a user's grants was reading two entries that mean nothing. A permission nobody enforces is worse
 * than a missing one: it looks like a capability.
 */
public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String PROMPT_MANAGE = "PROMPT_MANAGE";
    public static final String PROMPT_ADMIN = "PROMPT_ADMIN";
    public static final String AGENT_MANAGE = "AGENT_MANAGE";
    public static final String AGENT_ADMIN = "AGENT_ADMIN";
    public static final String CHAT_SEND = "CHAT_SEND";
    public static final String CHAT_SESSION_MANAGE = "CHAT_SESSION_MANAGE";

    public static final java.util.List<String> DEFAULT_PERMISSIONS = java.util.List.of(
            PROMPT_MANAGE,
            PROMPT_ADMIN,
            AGENT_MANAGE,
            AGENT_ADMIN,
            CHAT_SEND,
            CHAT_SESSION_MANAGE
    );
}
