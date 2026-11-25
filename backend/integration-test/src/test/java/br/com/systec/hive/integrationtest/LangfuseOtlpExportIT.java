package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
import br.com.systec.hive.config.LangfuseOtlpSpanSink;
import br.com.systec.hive.integrationtest.support.OtlpCaptureServer;
import br.com.systec.hive.integrationtest.support.OtlpPayload;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** OTL-01/02/03/05/10/11/12: contrato da requisição OTLP/HTTP protobuf capturada no fakeserver. */
class LangfuseOtlpExportIT {

    @Test
    void when_turnIsExported_then_requestIsPostProtobufWithBasicV4HeaderAndRoot() throws Exception {
        String pk = UUID.randomUUID().toString();
        String sk = UUID.randomUUID().toString();
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = template();
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink sink = activeSink(server.endpoint(), pk, sk, registry)) {
            TurnReply reply = turn(sink, session, template);
            new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                    AiProvider.OPENAI, "gpt-4o-mini", template, List.of(), reply, 12L, turn, "client-9");
            sink.flush();

            assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
            assertThat(server.requests()).hasSize(1);
            OtlpCaptureServer.Captured request = server.requests().getFirst();
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo(LangfuseOtlpExporterConfig.OTLP_TRACES_PATH);
            assertThat(request.header("content-type")).isEqualTo("application/x-protobuf");
            assertThat(request.header("content-type")).isNotEqualTo("application/json");
            assertThat(request.header("x-langfuse-ingestion-version")).isEqualTo("4");

            String authorization = request.header("authorization");
            assertThat(authorization).startsWith("Basic ");
            assertThat(new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8))
                    .isEqualTo(pk + ":" + sk);

            ExportTraceServiceRequest payload = OtlpPayload.decode(request.body());
            assertThat(payload.getResourceSpansCount()).isPositive();
            List<Span> spans = OtlpPayload.spans(payload);
            assertThat(spans).hasSize(2);
            assertThat(spans).allSatisfy(span -> assertThat(span.getName())
                    .isIn("chat.turn", "chat.provider.call"));

            Span root = spans.stream().filter(span -> span.getName().equals("chat.turn"))
                    .findFirst().orElseThrow();
            assertThat(root.getParentSpanId().isEmpty()).as("root sem pai").isTrue();
            Span child = spans.stream().filter(span -> span.getName().equals("chat.provider.call"))
                    .findFirst().orElseThrow();
            assertThat(child.getParentSpanId()).as("filho aponta para o root").isEqualTo(root.getSpanId());
            assertThat(child.getTraceId()).as("mesmo trace do root").isEqualTo(root.getTraceId());
            assertThat(root.getStartTimeUnixNano()).isPositive();
            assertThat(root.getEndTimeUnixNano()).isGreaterThanOrEqualTo(root.getStartTimeUnixNano());
            Map<String, String> attributes = OtlpPayload.attributes(root);
            assertThat(attributes).containsEntry("langfuse.observation.type", "span")
                    .containsEntry("langfuse.trace.name", "chat.turn")
                    .containsEntry("langfuse.session.id", session.toString())
                    .containsEntry("langfuse.user.id", "client-9")
                    .containsEntry("langfuse.trace.metadata.tenant_id", tenant.toString())
                    .containsEntry("langfuse.trace.metadata.turn_id", turn.toString())
                    .containsEntry("langfuse.trace.metadata.prompt_template_id", template.getId().toString());

            assertThat(server.requests()).allSatisfy(captured -> {
                assertThat(captured.path()).isNotEqualTo("/api/public/ingestion");
                assertThat(captured.path()).doesNotContain("/v1/metrics", "/v1/logs");
            });
            assertThat(new String(request.body(), StandardCharsets.ISO_8859_1)).doesNotContain(pk, sk);
            assertThat(registry.scrape()).contains("chat_telemetry_export_failures_total 0.0")
                    .doesNotContain("chat_telemetry_export_failures_total 1.0");
        }
    }

    private static LangfuseOtlpSpanSink activeSink(String endpoint, String pk, String sk,
                                                   PrometheusMeterRegistry registry) {
        return (LangfuseOtlpSpanSink) LangfuseOtlpExporterConfig.create(registry, true, endpoint, pk, sk);
    }

    private static PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        return template;
    }

    private static TurnReply turn(LangfuseOtlpSpanSink sink, UUID session, PromptTemplate template) {
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        return ChatResponse.builder()
                                .aiMessage(AiMessage.from("resposta"))
                                .tokenUsage(new TokenUsage(120, 340, 460))
                                .finishReason(FinishReason.STOP)
                                .build();
                    }

                    @Override
                    public ModelProvider provider() {
                        return ModelProvider.OPEN_AI;
                    }
                };
            }
        };
        AssistantTurnGateway gateway = new AssistantTurnGateway(factory, LlmRetryPolicy.withoutDelay(), sink);
        return gateway.execute(new TurnRequest(session, TurnPrompts.from(template), AiProvider.OPENAI,
                "gpt-4o-mini", UUID.randomUUID().toString(), null, List.of(new UserMessage("pergunta"))));
    }
}
