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

/** RED-07: input/output só no filho generation; root permanece sem ambas as chaves. */
class GenerationChildTextIT {

    @Test
    void when_turnIsExported_then_textKeysExistOnlyOnGenerationChild() throws Exception {
        RedactionFixtureSupport.enableTelemetryExplicitly();
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = RedactionFixtureSupport.template();
        String pk = UUID.randomUUID().toString();
        String sk = UUID.randomUUID().toString();

        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink sink = activeSink(server.endpoint(), pk, sk)) {
            AssistantTurnGateway gateway = RedactionFixtureSupport.gateway((ChatSpanSink) sink, new TextRedactor());
            TurnReply reply = gateway.execute(RedactionFixtureSupport.request(session, template));
            new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                    br.com.systec.hive.provider.api.AiProvider.OPENAI, "gpt-4o-mini", template,
                    List.of(), reply, 12L, turn, "client-9");
            sink.flush();

            OtlpCaptureServer.Captured captured = server.requests().getFirst();
            assertThat(captured.header("content-type")).isEqualTo(LangfuseOtlpExporterConfig.CONTENT_TYPE_PROTOBUF);
            ExportTraceServiceRequest payload = OtlpPayload.decode(captured.body());
            List<Span> spans = OtlpPayload.spans(payload);
            assertThat(spans).hasSize(2);
            Span root = spans.stream().filter(s -> s.getName().equals("chat.turn")).findFirst().orElseThrow();
            Span child = spans.stream().filter(s -> s.getName().equals("chat.provider.call")).findFirst().orElseThrow();

            Map<String, String> rootAttributes = OtlpPayload.attributes(root);
            Map<String, String> childAttributes = OtlpPayload.attributes(child);
            assertThat(rootAttributes).doesNotContainKeys("langfuse.observation.input", "langfuse.observation.output");
            assertThat(childAttributes).containsKeys("langfuse.observation.input", "langfuse.observation.output");
            assertThat(childAttributes.get("langfuse.observation.type")).isEqualTo("generation");
            assertThat(childAttributes.get("langfuse.observation.input")).contains("[REDACTED:cpf]", "[REDACTED:cnpj]");
            assertThat(childAttributes.get("langfuse.observation.output")).contains("[REDACTED:secret]");
            for (String literal : RedactionFixtureSupport.LITERALS) {
                assertThat(childAttributes.get("langfuse.observation.input")).doesNotContain(literal);
                assertThat(childAttributes.get("langfuse.observation.output")).doesNotContain(literal);
            }
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
