package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;

import java.util.Map;
import java.util.Set;

/** Mapeamento manual das chaves congeladas da "Interface congelada" do ADR-001. */
public final class SpanAttributeMapper {

    public static final String ROOT_NAME = "chat.turn";
    public static final String CHILD_NAME = "chat.provider.call";

    public static final String KEY_TYPE = "langfuse.observation.type";
    public static final String KEY_TRACE_NAME = "langfuse.trace.name";
    public static final String KEY_SESSION = "langfuse.session.id";
    public static final String KEY_USER = "langfuse.user.id";
    public static final String KEY_TENANT = "langfuse.trace.metadata.tenant_id";
    public static final String KEY_TURN = "langfuse.trace.metadata.turn_id";
    public static final String KEY_PROMPT_TEMPLATE = "langfuse.trace.metadata.prompt_template_id";
    public static final String KEY_ATTEMPT = "langfuse.observation.metadata.attempt";
    public static final String KEY_LEVEL = "langfuse.observation.level";
    public static final String KEY_STATUS_MESSAGE = "langfuse.observation.status_message";
    // ADR-003: texto redigido, somente no filho generation. Não entram no conjunto ADR-001 acima.
    public static final String KEY_INPUT = "langfuse.observation.input";
    public static final String KEY_OUTPUT = "langfuse.observation.output";

    public static final Set<String> FROZEN_LANGFUSE_KEYS = Set.of(
            KEY_TYPE, KEY_TRACE_NAME, KEY_SESSION, KEY_USER, KEY_TENANT, KEY_TURN,
            KEY_PROMPT_TEMPLATE, KEY_ATTEMPT, KEY_LEVEL, KEY_STATUS_MESSAGE);

    private SpanAttributeMapper() {
    }

    public static void typeAndTrace(Map<String, String> attributes, String type) {
        attributes.put(KEY_TYPE, type);
        attributes.put(KEY_TRACE_NAME, ROOT_NAME);
    }

    /** Correlação vem do `ChatTrace` do turno; campo ausente significa chave ausente, nunca "null". */
    public static void applyCorrelation(Map<String, String> attributes, ChatTrace trace) {
        if (trace == null) {
            return;
        }
        putIfPresent(attributes, KEY_SESSION, trace.sessionId());
        putIfPresent(attributes, KEY_USER, trace.clientId());
        putIfPresent(attributes, KEY_TENANT, trace.tenantId());
        putIfPresent(attributes, KEY_TURN, trace.turnId());
        putIfPresent(attributes, KEY_PROMPT_TEMPLATE, trace.promptTemplateId());
    }

    private static void putIfPresent(Map<String, String> attributes, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            attributes.put(key, text);
        }
    }
}
