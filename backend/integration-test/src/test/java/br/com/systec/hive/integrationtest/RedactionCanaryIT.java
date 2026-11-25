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
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-11: canário sobre protobuf decodificado — 0 literal no payload e 0 texto bruto em log. */
class RedactionCanaryIT {

    @Test
    void when_sensitiveFixtureIsExported_then_decodedProtobufAndLogContainOnlyPlaceholders() throws Exception {
        RedactionFixtureSupport.enableTelemetryExplicitly();
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = RedactionFixtureSupport.template();
        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink sink = activeSink(server.endpoint(),
                     UUID.randomUUID().toString(), UUID.randomUUID().toString())) {
            AssistantTurnGateway gateway = RedactionFixtureSupport.gateway((ChatSpanSink) sink, new TextRedactor());
            TurnReply reply = gateway.execute(RedactionFixtureSupport.request(session, template));
            new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                    br.com.systec.hive.provider.api.AiProvider.OPENAI, "gpt-4o-mini", template,
                    List.of(), reply, 12L, turn, "client-9");
            sink.flush();

            OtlpCaptureServer.Captured captured = server.requests().getFirst();
            assertThat(captured.header("content-type")).isEqualTo(LangfuseOtlpExporterConfig.CONTENT_TYPE_PROTOBUF);
            ExportTraceServiceRequest payload = OtlpPayload.decode(captured.body());
            String decoded = payload.toString();
            for (String literal : RedactionFixtureSupport.LITERALS) {
                assertThat(decoded).doesNotContain(literal);
            }
            assertThat(decoded).doesNotContain("sk-");
            assertThat(decoded).contains("[REDACTED:cpf]", "[REDACTED:cnpj]", "[REDACTED:email]",
                    "[REDACTED:phone]", "[REDACTED:secret]");

            Span child = OtlpPayload.spans(payload).stream()
                    .filter(span -> span.getName().equals("chat.provider.call"))
                    .findFirst().orElseThrow();
            Map<String, String> attributes = OtlpPayload.attributes(child);
            assertThat(attributes).containsKeys("langfuse.observation.input", "langfuse.observation.output");

            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            for (String literal : RedactionFixtureSupport.LITERALS) {
                assertThat(logged).doesNotContain(literal);
            }
            assertThat(logged).doesNotContain("sk-");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
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
