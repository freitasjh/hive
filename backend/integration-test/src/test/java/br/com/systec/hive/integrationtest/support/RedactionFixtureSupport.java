package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.config.LangfuseOtlpExporterConfig;
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

import java.util.List;
import java.util.UUID;

/** Fixture sensível canônica do ADR-003 e montagem do gateway com provider fake. */
public final class RedactionFixtureSupport {

    public static final String RAW = "CPF 123.456.789-09; CNPJ 12.345.678/0001-90; "
            + "CNPJ 12.ABC.345/01DE-35; email joao.silva@example.com; fone (11) 98765-4321; "
            + "token sk-abcdef1234567890; senha=SuperSecret123; "
            + "CNPJ 12abc34501de35; CNPJ 12.abc.345/01de-35; "
            + "{\"password\":\"SuperSecret123\"}; \"senha\" : \"SuperSecret123\"";

    public static final List<String> LITERALS = List.of(
            "123.456.789-09", "12.345.678/0001-90", "12.ABC.345/01DE-35",
            "joao.silva@example.com", "(11) 98765-4321", "sk-abcdef1234567890", "SuperSecret123",
            "12abc34501de35", "12.abc.345/01de-35", "\"password\":\"SuperSecret123\"",
            "\"senha\" : \"SuperSecret123\"");

    private RedactionFixtureSupport() {
    }

    public static void enableTelemetryExplicitly() {
        System.setProperty(LangfuseOtlpExporterConfig.ENABLE_ENV, "true");
        if (!"true".equals(System.getProperty(LangfuseOtlpExporterConfig.ENABLE_ENV))) {
            throw new IllegalStateException("HIVE_TELEMETRY_ENABLED deve ser explicitamente true no canário");
        }
    }

    public static PrometheusMeterRegistry registry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    public static PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        return template;
    }

    public static TurnRequest request(UUID session, PromptTemplate template) {
        return new TurnRequest(session, TurnPrompts.from(template), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test-fixture", null, List.of(new UserMessage(RAW)));
    }

    public static AssistantTurnGateway gateway(br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink sink,
                                               br.com.systec.hive.assistant.impl.diagnostics.TextRedactor redactor) {
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return echoModel();
            }
        };
        return new AssistantTurnGateway(factory, LlmRetryPolicy.withoutDelay(), sink, redactor);
    }

    public static ChatModel echoModel() {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(RAW))
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
}
