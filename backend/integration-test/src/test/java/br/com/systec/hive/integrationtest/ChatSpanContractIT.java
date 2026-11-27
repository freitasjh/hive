package br.com.systec.hive.integrationtest;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.service.AssistantTurnGateway;
import br.com.systec.hive.assistant.impl.service.TurnOutcomeMapper;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** SPAN-09: a prova de que o callback do listener alcança o root aberto no início do turno. */
class ChatSpanContractIT {

    private final List<List<ChatSpan>> published = new ArrayList<>();
    private final List<ChatTrace> recorded = new ArrayList<>();

    private AssistantTurnGateway gateway(ChatModel model) {
        LlmClientFactory<ChatModel> factory = new LlmClientFactory<>() {
            @Override
            public ChatModel createClient(AiProvider provider, String modelName, Double temperature,
                                          Integer maxTokens, Double topP, String apiKey, String baseUrl) {
                return model;
            }
        };
        return new AssistantTurnGateway(factory, LlmRetryPolicy.withoutDelay(), published::add);
    }

    private TurnOutcomeMapper mapper() {
        return new TurnOutcomeMapper(recorded::add, null);
    }

    private static PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
        return template;
    }

    private static TurnRequest request(UUID session, PromptTemplate template) {
        return new TurnRequest(session, TurnPrompts.from(template), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test", null, List.of(new UserMessage("pergunta")));
    }

    @Test
    void when_providerFailsFirstAttempt_then_oneRootTwoChildrenAndNoOrphansArePublished() {
        UUID session = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        PromptTemplate template = template();

        TurnReply reply = gateway(new FailingThenSuccessModel()).execute(request(session, template));
        mapper().resolve(session, tenant, template.getId(), AiProvider.OPENAI, "gpt-4o-mini", template,
                List.of(), reply, 12L, turn, "client-9");

        assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
        assertThat(recorded).hasSize(1);
        assertThat(published).hasSize(1);
        List<ChatSpan> spans = published.get(0);
        ChatSpan root = spans.stream().filter(ChatSpan::rootObservation).findFirst().orElseThrow();
        List<ChatSpan> children = spans.stream().filter(span -> !span.rootObservation()).toList();

        assertThat(spans).hasSize(3);
        assertThat(spans.stream().filter(ChatSpan::rootObservation)).hasSize(1);
        assertThat(children).hasSize(2);
        assertThat(children).allSatisfy(child -> {
            assertThat(child.parentSpanId()).as("órfão = filho sem root").isEqualTo(root.spanId());
            assertThat(child.traceId()).isEqualTo(root.traceId());
            assertThat(child.name()).isEqualTo("chat.provider.call");
            assertThat(child.attributes()).containsEntry("langfuse.trace.name", "chat.turn");
            assertThat(child.startTimeUnixNano()).isPositive();
            assertThat(child.endTimeUnixNano()).isGreaterThanOrEqualTo(child.startTimeUnixNano());
        });
        assertThat(root.name()).isEqualTo("chat.turn");
        assertThat(root.parentSpanId()).isNull();
        assertThat(root.startTimeUnixNano()).isPositive();
        assertThat(root.endTimeUnixNano()).isGreaterThanOrEqualTo(root.startTimeUnixNano());
        assertThat(children.get(0).attributes())
                .containsEntry("langfuse.observation.metadata.attempt", "1")
                .containsEntry("langfuse.observation.level", "ERROR")
                .containsEntry("langfuse.observation.status_message", "InternalServerException:server_error");
        assertThat(children.get(1).attributes())
                .containsEntry("langfuse.observation.metadata.attempt", "2")
                .containsEntry("langfuse.observation.level", "DEFAULT")
                .containsEntry("usage.input", "120")
                .containsEntry("usage.output", "340")
                .containsEntry("usage.total", "460");
        for (ChatSpan span : spans) {
            assertThat(span.attributes())
                    .containsEntry("langfuse.session.id", session.toString())
                    .containsEntry("langfuse.user.id", "client-9")
                    .containsEntry("langfuse.trace.metadata.tenant_id", tenant.toString())
                    .containsEntry("langfuse.trace.metadata.turn_id", turn.toString())
                    .containsEntry("langfuse.trace.metadata.prompt_template_id", template.getId().toString());
        }
    }

    @Test
    void when_theFlowIsHuman_then_userIdIsOmittedFromRootAndChildren() {
        UUID session = UUID.randomUUID();
        PromptTemplate template = template();

        TurnReply reply = gateway(new AlwaysSuccessModel()).execute(request(session, template));
        mapper().resolve(session, null, template.getId(), AiProvider.OPENAI, "gpt-4o-mini", template,
                List.of(), reply, 3L, null, null);

        List<ChatSpan> spans = published.get(0);
        assertThat(spans).hasSize(2);
        assertThat(spans).allSatisfy(span -> {
            assertThat(span.attributes()).doesNotContainKeys("langfuse.user.id",
                    "langfuse.trace.metadata.tenant_id", "langfuse.trace.metadata.turn_id");
            assertThat(span.startTimeUnixNano()).isPositive();
            assertThat(span.endTimeUnixNano()).isGreaterThanOrEqualTo(span.startTimeUnixNano());
        });
    }

    private static ChatResponse success() {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("resposta"))
                .tokenUsage(new TokenUsage(120, 340, 460))
                .finishReason(FinishReason.STOP)
                .build();
    }

    private static final class AlwaysSuccessModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return success();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }
    }

    private static final class FailingThenSuccessModel implements ChatModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            if (calls.incrementAndGet() == 1) {
                throw new InternalServerException("500 com SEGREDO do provedor");
            }
            return success();
        }

        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }
    }

    @Test
    void when_streamFailureIsRecorded_then_traceUsesClosedCodeInsteadOfThrowable() {
        String secret = "SEGREDO-SPAN-STREAM-77ab";
        var failed = new br.com.systec.hive.ai.api.domain.TurnStreamEvent.Failed(
                "stream_failed", new IllegalStateException(secret));
        ChatTrace trace = ChatTrace.builder(null, null, AiProvider.OPENAI, "model-x")
                .failure(failed.code()).build();
        assertThat(trace.failure()).isEqualTo("stream_failed").doesNotContain(secret);
        assertThat(failed.toString()).doesNotContain(secret);
    }
}
