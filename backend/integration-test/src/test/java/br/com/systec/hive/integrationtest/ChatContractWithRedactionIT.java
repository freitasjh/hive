package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.assistant.impl.diagnostics.TextRedactor;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.integrationtest.support.RedactionFixtureSupport;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-14: resposta, contagens e IDs idênticos com redator ativo, falhando e sem export. */
class ChatContractWithRedactionIT {

    @Test
    void when_redactionIsActiveFailingOrAbsent_then_chatReplyIsIdentical() {
        PromptTemplate template = RedactionFixtureSupport.template();
        TurnReply active = replyWith(new TextRedactor(), ChatSpanSink.noop(), template);
        TurnReply failing = replyWith(new TextRedactor(raw -> {
            throw new IllegalStateException("forced");
        }), ChatSpanSink.noop(), template);
        TurnReply noExport = replyWith(new TextRedactor(), ChatSpanSink.noop(), template);

        assertThat(failing.status()).isEqualTo(active.status());
        assertThat(failing.text()).isEqualTo(active.text());
        assertThat(failing.promptMessages()).isEqualTo(active.promptMessages());
        assertThat(failing.promptChars()).isEqualTo(active.promptChars());
        assertThat(failing.promptTokens()).isEqualTo(active.promptTokens());
        assertThat(failing.completionTokens()).isEqualTo(active.completionTokens());
        assertThat(failing.totalTokens()).isEqualTo(active.totalTokens());
        assertThat(failing.finishReason()).isEqualTo(active.finishReason());
        assertThat(noExport).isEqualTo(active);
        assertThat(active.status()).isEqualTo(TurnReply.Status.ANSWERED);
        assertThat(active.text()).isEqualTo(RedactionFixtureSupport.RAW);
    }

    private static TurnReply replyWith(TextRedactor redactor, ChatSpanSink sink, PromptTemplate template) {
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        AssistantTurnGateway gateway = RedactionFixtureSupport.gateway(sink, redactor);
        TurnReply reply = gateway.execute(RedactionFixtureSupport.request(session, template));
        new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                AiProvider.OPENAI, "gpt-4o-mini", template, List.of(), reply, 12L, turn, "client-9");
        return reply;
    }
}
