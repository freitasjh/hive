package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** SPAN-12: correlação é thread-local por turno — nenhum atributo cruza tenant ou turno. */
class TurnScopeIsolationIT {

    @Test
    void when_twoTenantTurnsRunConcurrently_then_eachSpanCarriesOnlyItsOwnTenant() throws Exception {
        UUID firstTenant = UUID.randomUUID();
        UUID secondTenant = UUID.randomUUID();
        List<List<ChatSpan>> firstSink = new ArrayList<>();
        List<List<ChatSpan>> secondSink = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = threads.submit(() -> runTurn(firstTenant, "client-a", firstSink, start));
            Future<?> second = threads.submit(() -> runTurn(secondTenant, null, secondSink, start));
            start.countDown();
            first.get();
            second.get();
        } finally {
            threads.shutdownNow();
        }

        assertThat(firstSink).hasSize(1);
        assertThat(secondSink).hasSize(1);
        List<ChatSpan> firstSpans = firstSink.get(0);
        List<ChatSpan> secondSpans = secondSink.get(0);

        assertThat(firstSpans).allSatisfy(span -> {
            assertThat(span.traceId()).isEqualTo(firstSpans.get(0).traceId());
            assertThat(span.attributes())
                    .containsEntry("langfuse.trace.metadata.tenant_id", firstTenant.toString())
                    .containsEntry("langfuse.user.id", "client-a");
            assertThat(span.attributes().values()).noneMatch(value -> value.contains(secondTenant.toString()));
        });
        assertThat(secondSpans).allSatisfy(span -> {
            assertThat(span.traceId()).isEqualTo(secondSpans.get(0).traceId());
            assertThat(span.attributes())
                    .containsEntry("langfuse.trace.metadata.tenant_id", secondTenant.toString())
                    .doesNotContainKey("langfuse.user.id");
            assertThat(span.attributes().values()).noneMatch(value -> value.contains(firstTenant.toString()));
        });
        assertThat(firstSpans.get(0).traceId()).isNotEqualTo(secondSpans.get(0).traceId());
    }

    private static void runTurn(UUID tenant, String client, List<List<ChatSpan>> sink, CountDownLatch start) {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        UUID session = UUID.randomUUID();
        TurnRequest request = new TurnRequest(session, TurnPrompts.from(template), AiProvider.OPENAI,
                "gpt-4o-mini", "sk-test", null, List.of(new UserMessage("pergunta")));
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return new ChatModel() {
                    @Override
                    public ChatResponse doChat(ChatRequest chatRequest) {
                        return ChatResponse.builder().aiMessage(AiMessage.from("resposta")).build();
                    }

                    @Override
                    public ModelProvider provider() {
                        return ModelProvider.OPEN_AI;
                    }
                };
            }
        };
        AssistantTurnGateway gateway = new AssistantTurnGateway(factory, LlmRetryPolicy.withoutDelay(), sink::add);
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(new ArrayList<ChatTrace>()::add, null);
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        TurnReply reply = gateway.execute(request);
        mapper.resolve(session, tenant, template.getId(), AiProvider.OPENAI, "gpt-4o-mini", template,
                List.of(), reply, 1L, UUID.randomUUID(), client);
    }
}
