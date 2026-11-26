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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** OTL-04/07/13: exporter fora ou lento não altera resposta do chat; falha incrementa contador. */
class LangfuseOtlpBestEffortIT {

    @Test
    void when_exporterRefusesConnection_then_chatReplyIsUnchangedAndFailureCounterIncrements() throws Exception {
        PromptTemplate template = template();
        UUID session = UUID.randomUUID();
        AtomicInteger baselineCalls = new AtomicInteger();
        TurnReply baseline = turn(br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink.noop(),
                template, session, baselineCalls);
        new TurnOutcomeMapper(trace -> { }, null).resolve(session, null, template.getId(), AiProvider.OPENAI,
                "gpt-4o-mini", template, List.of(), baseline, 12L, null, null);

        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        String closedEndpoint = "http://127.0.0.1:" + closedPort() + LangfuseOtlpExporterConfig.OTLP_TRACES_PATH;
        AtomicInteger calls = new AtomicInteger();
        try (LangfuseOtlpSpanSink sink = activeSink(closedEndpoint, registry)) {
            TurnReply reply = turn(sink, template, session, calls);
            new TurnOutcomeMapper(trace -> { }, null).resolve(session, null, template.getId(), AiProvider.OPENAI,
                    "gpt-4o-mini", template, List.of(), reply, 12L, null, null);
            sink.flush();

            assertThat(reply.status()).isEqualTo(baseline.status());
            assertThat(reply.text()).isEqualTo(baseline.text());
            assertThat(reply.promptChars()).isEqualTo(baseline.promptChars());
            assertThat(calls.get()).isEqualTo(baselineCalls.get());
            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(registry.scrape()).contains("chat_telemetry_export_failures_total{sink=\"langfuse\"} 1.0"));
            assertThat(registry.scrape()).doesNotContain("chat.telemetry.export.failures.total");
        }
    }

    @Test
    void when_exporterIsSlow_then_publishDoesNotBlockTheChatThread() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        try (OtlpCaptureServer server = OtlpCaptureServer.start(() -> {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
             LangfuseOtlpSpanSink sink = activeSink(server.endpoint(), registry)) {
            long started = System.nanoTime();
            sink.publish(List.of(rootSpan()));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(elapsedMillis).isLessThan(300L);
            release.countDown();
            sink.flush();
            assertThat(server.requests()).hasSize(1);
        }
    }

    private static LangfuseOtlpSpanSink activeSink(String endpoint, PrometheusMeterRegistry registry) {
        return (LangfuseOtlpSpanSink) LangfuseOtlpExporterConfig.create(registry, true, endpoint,
                UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static br.com.systec.hive.assistant.impl.diagnostics.ChatSpan rootSpan() {
        java.time.Instant now = java.time.Instant.now();
        long epochNano = now.getEpochSecond() * 1_000_000_000L + now.getNano();
        return new br.com.systec.hive.assistant.impl.diagnostics.ChatSpan("chat.turn", "span",
                UUID.randomUUID().toString().replace("-", "").substring(0, 32),
                UUID.randomUUID().toString().replace("-", "").substring(0, 16), null, true,
                java.util.Map.of("langfuse.observation.type", "span"), epochNano, epochNano + 1_000_000L);
    }

    private static TurnReply turn(br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink sink,
                                  PromptTemplate template, UUID session, AtomicInteger calls) {
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        calls.incrementAndGet();
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

    private static PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        return template;
    }
}
