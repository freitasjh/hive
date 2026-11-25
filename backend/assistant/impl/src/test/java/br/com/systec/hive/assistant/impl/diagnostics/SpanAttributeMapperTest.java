package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpanAttributeMapperTest {

    @Test
    void when_allFiveIdsArePresent_then_allFrozenCorrelationKeysCarryTheExactValues() {
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID template = UUID.randomUUID();
        ChatTrace trace = ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini")
                .tenantId(tenant).turnId(turn).clientId("client-42").build();

        Map<String, String> attributes = new HashMap<>();
        SpanAttributeMapper.applyCorrelation(attributes, trace);

        assertThat(attributes).containsEntry("langfuse.session.id", session.toString())
                .containsEntry("langfuse.user.id", "client-42")
                .containsEntry("langfuse.trace.metadata.tenant_id", tenant.toString())
                .containsEntry("langfuse.trace.metadata.turn_id", turn.toString())
                .containsEntry("langfuse.trace.metadata.prompt_template_id", template.toString());
    }

    @Test
    void when_optionalIdsAreNull_then_theirKeysAreOmittedRatherThanStringified() {
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m")
                .build();

        Map<String, String> attributes = new HashMap<>();
        SpanAttributeMapper.applyCorrelation(attributes, trace);

        assertThat(attributes).doesNotContainKeys("langfuse.user.id", "langfuse.trace.metadata.tenant_id",
                "langfuse.trace.metadata.turn_id");
        assertThat(attributes.values()).doesNotContain("null");
    }

    @Test
    void when_nullTrace_then_nothingIsInvented() {
        Map<String, String> attributes = new HashMap<>();
        SpanAttributeMapper.applyCorrelation(attributes, null);
        assertThat(attributes).isEmpty();
    }
}
