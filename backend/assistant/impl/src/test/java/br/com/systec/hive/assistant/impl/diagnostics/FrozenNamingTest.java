package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenNamingTest {

    /** Transcrição literal da "Interface congelada" do ADR-001 — nenhuma chave pode entrar ou sair. */
    private static final Set<String> ADR_KEYS = Set.of(
            "langfuse.observation.type",
            "langfuse.trace.name",
            "langfuse.session.id",
            "langfuse.user.id",
            "langfuse.trace.metadata.tenant_id",
            "langfuse.trace.metadata.turn_id",
            "langfuse.trace.metadata.prompt_template_id",
            "langfuse.observation.metadata.attempt",
            "langfuse.observation.level",
            "langfuse.observation.status_message");

    @Test
    void when_frozenNamingIsRead_then_spanNamesAndLangfuseKeysMatchTheAdrExactly() {
        assertThat(SpanAttributeMapper.ROOT_NAME).isEqualTo("chat.turn");
        assertThat(SpanAttributeMapper.CHILD_NAME).isEqualTo("chat.provider.call");
        assertThat(SpanAttributeMapper.FROZEN_LANGFUSE_KEYS).isEqualTo(ADR_KEYS);
    }

    @Test
    void when_aTurnIsPublished_then_emittedLangfuseKeysStayInsideTheFrozenSet() {
        List<List<ChatSpan>> published = new ArrayList<>();
        UUID session = UUID.randomUUID();
        UUID template = UUID.randomUUID();

        ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);
        ChatTurnSpanScope.attemptStarted();
        ChatTurnSpanScope.attemptFailed(new IllegalStateException("falha"));
        ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini")
                .tenantId(UUID.randomUUID()).turnId(UUID.randomUUID()).clientId("client-1").build());

        Set<String> emitted = published.stream()
                .flatMap(List::stream)
                .flatMap(span -> span.attributes().keySet().stream())
                .filter(key -> key.startsWith("langfuse."))
                .collect(Collectors.toSet());

        // ADR-005 acrescenta duas chaves ao filho generation; o conjunto ADR-001 permanece intacto.
        Set<String> allowed = new java.util.HashSet<>(SpanAttributeMapper.FROZEN_LANGFUSE_KEYS);
        allowed.add(SpanAttributeMapper.KEY_MODEL_NAME);
        allowed.add(SpanAttributeMapper.KEY_USAGE_DETAILS);
        assertThat(emitted).isNotEmpty().isSubsetOf(allowed);
    }
}
