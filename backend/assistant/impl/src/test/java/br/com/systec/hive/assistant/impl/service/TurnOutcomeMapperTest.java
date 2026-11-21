package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.exceptions.ConfigMissingException;
import br.com.systec.hive.assistant.api.exceptions.ModelReturnedEmptyException;
import br.com.systec.hive.assistant.api.exceptions.ModelUnavailableException;
import br.com.systec.hive.assistant.api.exceptions.ProviderException;
import br.com.systec.hive.provider.api.AiProvider;
import br.com.systec.hive.provider.api.OpencodeDiscoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three outcomes of a turn, and what each one costs the operator.
 *
 * <p>These assertions used to live inside {@code ChatServiceImplTest}, where every one of them had to
 * stub a session, a template and a message store in order to observe one classification. They are the
 * same three branches, moved with the code: the trace is recorded (or is not), the vendor cache is
 * evicted (or is not), and the exception that reaches the caller is the same one, with the same
 * message, that reached it before.
 *
 * <p>The answered branch is the one that returns; the other two raise. A turn that must not persist an
 * assistant row therefore never reaches the caller's second transaction — which is the property these
 * tests exist to hold while the code moves.
 */
@ExtendWith(MockitoExtension.class)
class TurnOutcomeMapperTest {

    @Mock
    private ChatTraceRecorder traceRecorder;

    @Mock
    private OpencodeDiscoveryService discoveryService;

    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID promptTemplateId = UUID.randomUUID();

    @Test
    void when_theProviderAnswered200WithNothing_then_labelsTheTraceAndThrows422() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        TurnReply reply = TurnReply.emptyResponse(2, 42, null, null, null, "LENGTH");

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 12L))
                .isInstanceOf(ModelReturnedEmptyException.class)
                .hasMessageContaining("gpt-4o-mini");

        // O rótulo é o fato que o operador precisa: o modelo não quebrou, devolveu nada de propósito.
        assertThat(recordedTrace().failure()).isEqualTo("empty_model_response");
        assertThat(recordedTrace().maxTokens()).isEqualTo(32);
        verify(discoveryService, never()).evict(any());
    }

    @Test
    void when_theFailureIsAlreadyAProjectException_then_itTravelsUnchangedAndWritesNoTrace() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        ConfigMissingException alreadyClassified = new ConfigMissingException("OPENAI");
        TurnReply reply = TurnReply.failed(2, 42, alreadyClassified);

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 12L))
                .isSameAs(alreadyClassified);

        verifyNoInteractions(traceRecorder);
        verify(discoveryService, never()).evict(any());
    }

    /**
     * The stale-model case: a 404 whose body names the model. The vendor list is evicted — it is the
     * only cache that can be wrong now — and the operator gets a 422 about the model.
     */
    @Test
    void when_theModelIsGone_then_evictsTheVendorListAndThrows422() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        TurnReply reply = TurnReply.failed(3, 128,
                new dev.langchain4j.exception.ModelNotFoundException("model_not_found: mimo-v2.5"));

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENCODE_GO, "mimo-v2.5", template(), history(), reply, 900L))
                .isInstanceOf(ModelUnavailableException.class)
                .hasMessageContaining("mimo-v2.5")
                .hasMessageContaining("OPENCODE_GO");

        assertThat(recordedTrace().failure()).isEqualTo("ModelNotFoundException:model_not_found");
        verify(discoveryService).evict(AiProvider.OPENCODE_GO);
    }

    /**
     * The same 404 with an HTML body is a missing {@code /v1} in the baseUrl — a configuration error,
     * not a removed model. It must not evict the cache, and it must not become a 422.
     */
    @Test
    void when_the404IsARoute_then_keepsTheCacheAndThrows502() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        TurnReply reply = TurnReply.failed(3, 128, new dev.langchain4j.exception.HttpException(404,
                "<!DOCTYPE html><html><body>opencode.ai</body></html>"));

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENCODE_GO, "mimo-v2.5", template(), history(), reply, 5L))
                .isInstanceOf(ProviderException.class)
                .isNotInstanceOf(ModelUnavailableException.class);

        verify(discoveryService, never()).evict(any());
        assertThat(recordedTrace().failure()).isEqualTo("HttpException:not_found");
    }

    @Test
    void when_theProviderFailsForAnyOtherReason_then_recordsTheClassificationAndThrows502() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        RuntimeException failure = new RuntimeException("LLM down");
        TurnReply reply = TurnReply.failed(2, 42, failure);

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 7L))
                .isInstanceOf(ProviderException.class)
                .getCause().isSameAs(failure);

        assertThat(recordedTrace().failure()).isEqualTo("RuntimeException:unexpected");
        verify(discoveryService, never()).evict(any());
    }

    @Test
    void when_theTurnWasAnswered_then_recordsATraceAndReturns() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        TurnReply reply = TurnReply.answered("assistant reply", 2, 42, 120, 340, 460, "STOP");

        assertThatCode(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 11L)).doesNotThrowAnyException();

        ChatTrace trace = recordedTrace();
        assertThat(trace.failure()).isNull();
        assertThat(trace.promptChars()).isEqualTo(42);
        assertThat(trace.promptMessages()).isEqualTo(2);
        assertThat(trace.elapsedMs()).isEqualTo(11L);
        verify(discoveryService, never()).evict(any());
    }

    /**
     * The closed branch. The enum has three members and the two above answer for two of them, so this
     * is reachable only by a port implementation that returns something else — which is exactly the
     * wiring mistake that used to reach {@code chat_message.content NOT NULL} as a 409 nobody could
     * diagnose. Pinned here so the guard cannot be "cleaned up" as dead code.
     */
    @Test
    void when_theReplyCarriesNoKnownStatus_then_theGuardRefusesIt() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(traceRecorder, discoveryService);
        TurnReply reply = mock(TurnReply.class);
        when(reply.status()).thenReturn(null);

        assertThatThrownBy(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unreachable turn status");
    }

    /**
     * A trace recorder that fails must not turn a healthy answer into a 502: the telemetry is the
     * only thing that broke, and the answer is still the answer.
     */
    @Test
    void when_theTraceRecorderFails_then_theAnswerSurvives() {
        TurnOutcomeMapper mapper = new TurnOutcomeMapper(trace -> {
            throw new IllegalStateException("trace down");
        }, discoveryService);
        TurnReply reply = TurnReply.answered("resposta", 2, 42, null, null, null, null);

        assertThatCode(() -> mapper.resolve(sessionId, tenantId, promptTemplateId,
                AiProvider.OPENAI, "gpt-4o-mini", template(), history(), reply, 1L)).doesNotThrowAnyException();
    }

    private PromptTemplate template() {
        PromptTemplate template = new PromptTemplate();
        template.setId(promptTemplateId);
        template.setTemplate("Voce e um assistente.");
        template.setInferenceParams(new InferenceParams(0.7, null, 32));
        return template;
    }

    private List<ChatMessage> history() {
        ChatMessage message = new ChatMessage();
        message.setContent("Hello world");
        return List.of(message);
    }

    private ChatTrace recordedTrace() {
        ArgumentCaptor<ChatTrace> captor = ArgumentCaptor.forClass(ChatTrace.class);
        verify(traceRecorder, times(1)).record(captor.capture());
        return captor.getValue();
    }
}
