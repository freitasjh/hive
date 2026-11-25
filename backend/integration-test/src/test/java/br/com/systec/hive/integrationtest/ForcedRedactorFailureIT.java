package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.assistant.impl.diagnostics.TextRedactor;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
import br.com.systec.hive.config.LangfuseOtlpSpanSink;
import br.com.systec.hive.integrationtest.support.OtlpCaptureServer;
import br.com.systec.hive.integrationtest.support.OtlpPayload;
import br.com.systec.hive.integrationtest.support.RedactionFixtureSupport;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-12: redator forçado a falhar ⇒ span diagnósticável sem input/output e contador +1. */
class ForcedRedactorFailureIT {

    @Test
    void when_redactorIsForcedToFail_then_childKeepsMetadataButHasNoText() throws Exception {
        RedactionFixtureSupport.enableTelemetryExplicitly();
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = RedactionFixtureSupport.template();
        TextRedactor failing = new TextRedactor(raw -> {
            throw new IllegalStateException(RedactionFixtureSupport.RAW);
        });

        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink sink = activeSink(server.endpoint(),
                     UUID.randomUUID().toString(), UUID.randomUUID().toString())) {
            AssistantTurnGateway gateway = RedactionFixtureSupport.gateway((ChatSpanSink) sink, failing);
            TurnReply reply = gateway.execute(RedactionFixtureSupport.request(session, template));
            new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                    br.com.systec.hive.provider.api.AiProvider.OPENAI, "gpt-4o-mini", template,
                    List.of(), reply, 12L, turn, "client-9");
            sink.flush();

            assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
            assertThat(failing.failureCount()).isEqualTo(1);

            ExportTraceServiceRequest payload = OtlpPayload.decode(server.requests().getFirst().body());
            for (String literal : RedactionFixtureSupport.LITERALS) {
                assertThat(payload.toString()).doesNotContain(literal);
            }
            Span child = OtlpPayload.spans(payload).stream()
                    .filter(span -> span.getName().equals("chat.provider.call"))
                    .findFirst().orElseThrow();
            Map<String, String> attributes = OtlpPayload.attributes(child);
            assertThat(attributes).doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
            assertThat(attributes)
                    .containsEntry("provider", "OPENAI")
                    .containsEntry("model", "gpt-4o-mini")
                    .containsEntry("usage.input", "120")
                    .containsEntry("usage.output", "340")
                    .containsEntry("usage.total", "460")
                    .containsEntry("langfuse.observation.metadata.attempt", "1")
                    .containsEntry("langfuse.observation.level", "DEFAULT");
        }
    }

    @AfterEach
    void clearTelemetryFlag() {
        System.clearProperty(LangfuseOtlpExporterConfig.ENABLE_ENV);
    }

    private static LangfuseOtlpSpanSink activeSink(String endpoint, String pk, String sk) {
        return (LangfuseOtlpSpanSink) LangfuseOtlpExporterConfig.create(
                RedactionFixtureSupport.registry(), true, endpoint, pk, sk);
    }
}
