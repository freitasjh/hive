package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.LlmClientFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The generation seam, on its own: the prompt it builds and the measurements it reports.
 *
 * <p>These are the paths that found the defects this code was validated against — a reasoning model
 * that answers with nothing, and a {@code 404} that means two different things depending on the body
 * the provider sent. They used to be reachable only through a whole chat turn, where a failure test had
 * to stub a session, a prompt template and a message store to observe one classification.
 *
 * <p>The model-not-found rule left this class for {@code agent/api} (D5) and its tests left with it —
 * they are in {@code ModelNotFoundTest} now, unchanged. What this class still owns is the prompt, the
 * call and the measurements.
 */
@ExtendWith(MockitoExtension.class)
class LangChain4jTurnGatewayTest {

    @Mock
    private LlmClientFactory<ChatModel> factory;
    @Mock
    private ChatModel chatModel;

    private ChatTurnGatewayUnderTest gateway;
    private PromptTemplate template;
    private UUID sessionId;

    /** Named so the assertion reads as "the gateway", not as a subclass detail. */
    private static final class ChatTurnGatewayUnderTest extends AssistantTurnGateway {
        private ChatTurnGatewayUnderTest(LlmClientFactory<ChatModel> factory) {
            super(factory);
        }
    }

    @BeforeEach
    void setUp() {
        gateway = new ChatTurnGatewayUnderTest(factory);
        sessionId = UUID.randomUUID();
        template = new PromptTemplate();
        template.setId(UUID.randomUUID());
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, null));
    }

    private void stubClient() {
        when(factory.createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"), eq(0.7),
                any(), any(), eq("sk-test"), any(), eq(sessionId))).thenReturn(chatModel);
        // O proxy AiService chama os defaults de ChatModel; um mock Mockito devolve null neles.
        lenient().when(chatModel.defaultRequestParameters()).thenReturn(DefaultChatRequestParameters.EMPTY);
        lenient().when(chatModel.listeners()).thenReturn(List.of());
    }

    private TurnRequest request() {
        return new TurnRequest(sessionId, TurnPrompts.from(template), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test", null, List.of(new UserMessage("pergunta")));
    }

    /**
     * ADR-020: o proxy AiService chama {@code chat(ChatRequest)}; o antigo {@code chat(List)} era um
     * default que delegava a ele. {@code any()} sozinho é ambíguo entre as sobrecargas.
     */
    private static ChatRequest anyPrompt() {
        return any(ChatRequest.class);
    }

    private List<ChatMessage> capturedPrompt() {
        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(captor.capture());
        return captor.getValue().messages();
    }

    // ------------------------------------------------------------------ the prompt

    @Test
    void when_theTemplateHasText_then_itLeadsThePrompt() {
        stubClient();
        when(chatModel.chat(anyPrompt())).thenReturn(answer("resposta", null, null));

        gateway.execute(request());

        List<ChatMessage> prompt = capturedPrompt();
        assertThat(prompt.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) prompt.get(0)).text()).isEqualTo("Voce e um assistente.");
    }

    /**
     * ADR-020/RF-04: com o seed removido do banco, um template em branco usa
     * {@link AssistantTurnGateway#PROMPT} como fallback em código — nunca um {@code SystemMessage} vazio
     * (que instruiria o modelo a ser breve) e nunca nenhum system.
     */
    @Test
    void when_theTemplateHasBlankText_then_theCodeFallbackIsUsed() {
        template.setTemplate("   ");
        stubClient();
        when(chatModel.chat(anyPrompt())).thenReturn(answer("resposta", null, null));

        gateway.execute(request());

        assertThat(capturedPrompt().get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) capturedPrompt().get(0)).text()).isEqualTo(AssistantTurnGateway.PROMPT);
    }

    @Test
    void when_theTurnIsBuilt_then_theSystemPromptIsTheOnlyThingBeforeTheConversation() {
        stubClient();
        when(chatModel.chat(anyPrompt())).thenReturn(answer("resposta", null, null));

        gateway.execute(request());

        List<ChatMessage> prompt = capturedPrompt();
        assertThat(prompt).as("system prompt plus the question, and nothing else").hasSize(2);
        assertThat(((SystemMessage) prompt.get(0)).text()).isEqualTo("Voce e um assistente.");
        assertThat(prompt.get(1)).isInstanceOf(UserMessage.class);
    }

    // ------------------------------------------------------------------ the answer

    @Test
    void when_theModelAnswers_then_theReplyAndItsMeasurementsComeBack() {
        stubClient();
        when(chatModel.chat(anyPrompt()))
                .thenReturn(answer("resposta", new TokenUsage(120, 340, 460), FinishReason.STOP));

        TurnReply reply = gateway.execute(request());

        assertThat(reply.status()).isEqualTo(TurnReply.Status.ANSWERED);
        assertThat(reply.text()).isEqualTo("resposta");
        assertThat(reply.promptTokens()).isEqualTo(120);
        assertThat(reply.completionTokens()).isEqualTo(340);
        assertThat(reply.totalTokens()).isEqualTo(460);
        assertThat(reply.finishReason()).isEqualTo("STOP");
        assertThat(reply.failure()).isNull();
        assertThat(reply.promptMessages()).isEqualTo(capturedPrompt().size());
        assertThat(reply.promptChars()).isPositive();
    }

    /**
     * A turn that returned no text is a value, not an exception, because the trace for it needs the
     * finish reason and the token usage — and those exist only on the response object. This is the
     * defect this project shipped as a 409: {@code chat_message.content} is {@code NOT NULL}, so a
     * blank reply that reached the save became a data-integrity error that said nothing to the user.
     */
    @Test
    void when_theModelReturnsNoText_then_itIsReportedAsEmptyWithTheProvidersOwnFinishReason() {
        template.setInferenceParams(new InferenceParams(0.7, null, 32));
        stubClient();
        when(chatModel.chat(anyPrompt()))
                .thenReturn(dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(AiMessage.builder().text(null).build())
                        .finishReason(FinishReason.LENGTH)
                        .build());

        TurnReply reply = gateway.execute(request());

        assertThat(reply.status()).isEqualTo(TurnReply.Status.EMPTY_RESPONSE);
        assertThat(reply.text()).isNull();
        assertThat(reply.finishReason())
                .as("LENGTH is the fact that says the model spent its budget on reasoning")
                .isEqualTo("LENGTH");
        assertThat(reply.failure()).isNull();
        assertThat(reply.promptMessages()).isPositive();
    }

    /** The exact blank varies by model: {@code mimo-v2.5} returns null, {@code glm-5.2} returns "". */
    @Test
    void when_theModelReturnsBlankText_then_itIsAlsoEmpty() {
        stubClient();
        when(chatModel.chat(anyPrompt())).thenReturn(answer("   \n\t ", null, null));

        assertThat(gateway.execute(request()).status()).isEqualTo(TurnReply.Status.EMPTY_RESPONSE);
    }

    // ------------------------------------------------------------------ the failure

    /**
     * A provider failure is returned rather than thrown, carrying the size of the prompt that was
     * actually sent. Throwing would have forced the turn to reconstruct a number it never measured —
     * and the operator reading a provider failure is precisely the person who needs to know how large
     * the request was.
     */
    @Test
    void when_theProviderFails_then_theFailureIsCarriedWithThePromptThatWasSent() {
        stubClient();
        when(chatModel.chat(anyPrompt())).thenThrow(new IllegalStateException("provider down"));

        TurnReply reply = gateway.execute(request());

        assertThat(reply.status()).isEqualTo(TurnReply.Status.FAILED);
        assertThat(reply.failure())
                .as("the provider's own exception, unwrapped, so the turn classifies the same object")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("provider down");
        assertThat(reply.promptMessages()).isEqualTo(capturedPrompt().size());
        assertThat(reply.promptChars()).isPositive();
        assertThat(reply.promptTokens()).isNull();
        assertThat(reply.finishReason()).isNull();
        assertThat(reply.text()).isNull();
    }

    @Test
    void when_theClientCannotEvenBeBuilt_then_theFailureIsCarriedToo() {
        when(factory.createClientWithSession(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("no transport"));

        TurnReply reply = gateway.execute(request());

        assertThat(reply.status()).isEqualTo(TurnReply.Status.FAILED);
        assertThat(reply.failure()).hasMessage("no transport");
    }

    /**
     * A read timeout is not repeated. The SDK's own retry treats it as retriable, and with a 30 s
     * ceiling and two retries this project shipped a ~90 s stall before the user saw an error.
     */
    @Test
    void when_theProviderTimesOut_then_theCallIsMadeOnce() {
        stubClient();
        when(chatModel.chat(anyPrompt())).thenThrow(
                new dev.langchain4j.exception.TimeoutException(
                        new java.net.http.HttpTimeoutException("read timed out")));

        TurnReply reply = gateway.execute(request());

        assertThat(reply.status()).isEqualTo(TurnReply.Status.FAILED);
        verify(chatModel, times(1)).chat(anyPrompt());
    }

    private static dev.langchain4j.model.chat.response.ChatResponse answer(
            String text, TokenUsage usage, FinishReason finishReason) {
        var builder = dev.langchain4j.model.chat.response.ChatResponse.builder()
                .aiMessage(text == null ? AiMessage.builder().text(null).build() : AiMessage.from(text));
        if (usage != null) {
            builder.tokenUsage(usage);
        }
        if (finishReason != null) {
            builder.finishReason(finishReason);
        }
        return builder.build();
    }

    // ------------------------------------------------------------------ nothing renders a secret

    /**
     * A record renders every component by default, and this one now crosses a boundary where the
     * credential used to be a parameter that died in a call frame. So the two fields that must not be
     * printed are pinned here rather than trusted: the key, and the provider's own exception message.
     */
    @Test
    void when_theRequestIsRendered_then_theCredentialAndTheConversationAreNot() {
        TurnRequest request = new TurnRequest(sessionId, TurnPrompts.from(template), AiProvider.OPENAI, "gpt-4o-mini",
                "sk-super-secret-value", "https://api.example/v1",
                List.of(new UserMessage("PERGUNTA SIGILOSA do usuario")));

        String rendered = request.toString();

        assertThat(rendered)
                .doesNotContain("sk-super-secret-value")
                .doesNotContain("PERGUNTA SIGILOSA do usuario")
                .contains("apiKey=***");
        // Still enough to diagnose a turn with.
        assertThat(rendered)
                .contains("promptTemplateId=" + template.getId())
                .contains("model=gpt-4o-mini")
                .contains("conversation=1 message(s)");
    }

    @Test
    void when_theReplyIsRendered_then_theFailureIsNamedButNotQuoted() {
        // The HttpException message IS the provider's raw payload — the same text RagDiagnostic exists
        // to classify rather than quote, and the same text a log line would otherwise reprint.
        dev.langchain4j.exception.HttpException failure =
                new dev.langchain4j.exception.HttpException(404,
                        "{\"error\":{\"message\":\"API KEY sk-leaked-in-payload rejected\"}}");

        String rendered = TurnReply.failed(3, 412, failure).toString();

        assertThat(rendered)
                .doesNotContain("sk-leaked-in-payload")
                .doesNotContain("API KEY")
                .contains("failure=HttpException");
    }

    /** A failed reply with no failure is a gateway defect, and is refused where it is built. */
    @Test
    void when_aFailedReplyCarriesNoFailure_then_itIsRefusedAtConstruction() {
        assertThatThrownBy(() -> TurnReply.failed(1, 1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("failure");
    }

    /** And the same rule as the other records: a value that carries meaning is not defaulted. */
    @Test
    void when_theRequestCarriesNoPromptTemplate_then_itIsRefusedRatherThanPromptedWithoutOne() {
        // Um template nulo sairia sem prompt de sistema e sem limites de geração, sem dizer nada.
        assertThatThrownBy(() -> new TurnRequest(sessionId, null, AiProvider.OPENAI, "gpt-4o-mini",
                "sk-test", null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("promptTemplate");
    }

    @Test
    void when_theTemplateCarriesNoInferenceParams_then_theProviderDefaultsApply() {
        // null, e não um InferenceParams vazio Required: os três campos já são null lá dentro, e
        // fabricar um objeto só para devolver null três vezes esconderia a ausência.
        template.setInferenceParams(null);
        when(factory.createClientWithSession(eq(AiProvider.OPENAI), eq("gpt-4o-mini"),
                isNull(), isNull(), isNull(), eq("sk-test"), any(), eq(sessionId))).thenReturn(chatModel);
        when(chatModel.chat(anyPrompt())).thenReturn(answer("resposta", null, null));

        gateway.execute(request());

        assertThat(capturedPrompt()).isNotEmpty();
    }


}
