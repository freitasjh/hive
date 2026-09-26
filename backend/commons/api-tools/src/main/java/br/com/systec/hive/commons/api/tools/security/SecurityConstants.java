package br.com.systec.hive.commons.api.tools.security;

public final class SecurityConstants {

    private SecurityConstants() {
    }

    public static final String PROMPT_MANAGE = "PROMPT_MANAGE";
    public static final String PROMPT_ADMIN = "PROMPT_ADMIN";
    public static final String AGENT_MANAGE = "AGENT_MANAGE";
    public static final String AGENT_ADMIN = "AGENT_ADMIN";
    public static final String CHAT_SEND = "CHAT_SEND";
    public static final String CHAT_SESSION_MANAGE = "CHAT_SESSION_MANAGE";
    public static final String KNOWLEDGE_MANAGE = "KNOWLEDGE_MANAGE";
    public static final String KNOWLEDGE_READ = "KNOWLEDGE_READ";

    public static final java.util.List<String> DEFAULT_PERMISSIONS = java.util.List.of(
            PROMPT_MANAGE,
            PROMPT_ADMIN,
            AGENT_MANAGE,
            AGENT_ADMIN,
            CHAT_SEND,
            CHAT_SESSION_MANAGE,
            KNOWLEDGE_MANAGE,
            KNOWLEDGE_READ
    );
}
