package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.config.ChatMetricsRecorder;
import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
import br.com.systec.hive.config.LangfuseOtlpSpanSink;
import br.com.systec.hive.integrationtest.support.OtlpCaptureServer;
import br.com.systec.hive.integrationtest.support.OtlpPayload;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.FinishReason;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.Span;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** MET-09/10/11/12/16/17/23: custo inferido no Langfuse, zero custo enviado pelo backend. */
class LangfuseCostDerivationIT {

    private static final String QUERY = "{\"view\":\"observations\",\"metrics\":[{\"measure\":\"totalCost\","
            + "\"aggregation\":\"sum\"}],\"dimensions\":[{\"field\":\"providedModelName\"}],"
            + "\"filters\":[],\"orderBy\":[{\"field\":\"sum_totalCost\",\"direction\":\"desc\"}]}";

    @Test
    void when_turnHasOpenAiCachedUsage_then_payloadCarriesExclusiveBucketsAndNoCost() throws Exception {
        String pk = UUID.randomUUID().toString();
        String sk = UUID.randomUUID().toString();
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = template();
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        AtomicReference<String> metricsRequests = new AtomicReference<>("");

        HttpServer metrics = fakeMetricsApi(metricsRequests, "{\"data\":[{\"providedModelName\":"
                + "\"gpt-4o-mini\",\"sum_totalCost\":0.542}]}");
        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> { });
             LangfuseOtlpSpanSink otlp = activeSink(server.endpoint(), pk, sk, registry)) {
            ChatMetricsRecorder recorder = new ChatMetricsRecorder(registry);
            TurnReply reply = turn(otlp, template, session, true);

            new TurnOutcomeMapper(trace -> { }, null).resolve(session, tenant, template.getId(),
                    AiProvider.OPENAI, "gpt-4o-mini", template, List.of(), reply, 12L, turn, "client-9");
            otlp.flush();

            assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
            assertThat(server.requests()).hasSize(1);
            OtlpCaptureServer.Captured request = server.requests().getFirst();
            assertThat(request.path()).isEqualTo(LangfuseOtlpExporterConfig.OTLP_TRACES_PATH);
            assertThat(server.requests()).allSatisfy(captured -> {
                assertThat(captured.path()).doesNotContain("/v1/metrics", "/v1/logs");
                assertThat(captured.path()).isNotEqualTo("/api/public/ingestion");
            });

            ExportTraceServiceRequest payload = OtlpPayload.decode(request.body());
            List<Span> spans = OtlpPayload.spans(payload);
            assertThat(spans).hasSize(2);
            Span child = spans.stream().filter(span -> span.getName().equals("chat.provider.call"))
                    .findFirst().orElseThrow();
            Map<String, String> attributes = OtlpPayload.attributes(child);
            assertThat(attributes)
                    .containsEntry("langfuse.observation.type", "generation")
                    .containsEntry("langfuse.observation.model.name", "gpt-4o-mini")
                    .containsEntry("langfuse.observation.usage_details",
                            "{\"input\":86,\"output\":188,\"input_cached_tokens\":17817,\"total\":18091}");
            assertThat(attributes.keySet()).noneMatch(key -> key.contains("cost")
                    || key.equals("gen_ai.usage.cost") || key.equals("costDetails"));
            assertThat(new String(request.body(), StandardCharsets.ISO_8859_1)).doesNotContain("costDetails",
                    "langfuse.observation.cost_details", "gen_ai.usage.cost");

            String scrape = registry.scrape();
            assertThat(scrape).contains("chat_turns_total", "chat_provider_calls_total",
                    "chat_provider_failures_total", "chat_provider_latency_seconds",
                    "chat_telemetry_export_failures_total", "chat_telemetry_redaction_failures_total",
                    "chat_provider_tokens_sum");
            assertThat(scrape).doesNotContain("otelcol_", "gen_ai_client_token_usage");

            String response = callMetricsApi(metrics, QUERY);
            assertThat(java.net.URLDecoder.decode(metricsRequests.get(), StandardCharsets.UTF_8))
                    .isEqualTo("query=" + QUERY)
                    .doesNotContain("&measure=", "&aggregation=");
            assertThat(response).contains("sum_totalCost");
            assertThat(recorder.redactionCount()).isZero();
        } finally {
            metrics.stop(0);
        }
    }

    @Test
    void when_noModelDefinitionMatches_then_sumTotalCostIsZeroAndBackendDoesNotComputePrice() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer metrics = fakeMetricsApi(seen, "{\"data\":[{\"providedModelName\":\"sem-definicao\","
                + "\"sum_totalCost\":0}]}");
        try {
            String response = callMetricsApi(metrics, QUERY);

            assertThat(response).contains("sum_totalCost");
            assertThat(response).contains("\"sum_totalCost\":0");
            assertThat(seen.get()).doesNotContain("cost_details", "price");
        } finally {
            metrics.stop(0);
        }
    }

    private static String callMetricsApi(HttpServer server, String query) throws Exception {
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/public/v2/metrics?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static HttpServer fakeMetricsApi(AtomicReference<String> seen, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/v2/metrics", exchange -> {
            seen.set(exchange.getRequestURI().getRawQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
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

    /** Provider fake: usage OpenAI-style com `cached_tokens` (SDK 1.19.0,fonte verificada). */
    private static TurnReply turn(LangfuseOtlpSpanSink sink, PromptTemplate template, UUID session,
                                  boolean withCache) {
        OpenAiTokenUsage usage = OpenAiTokenUsage.builder()
                .inputTokenCount(17903)
                .inputTokensDetails(OpenAiTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(withCache ? 17817 : null).build())
                .outputTokenCount(188)
                .totalTokenCount(18091)
                .build();
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        return ChatResponse.builder().aiMessage(AiMessage.from("resposta"))
                                .tokenUsage(usage).finishReason(FinishReason.STOP).build();
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
