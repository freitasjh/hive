package br.com.systec.hive.integrationtest;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpan;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.assistant.impl.diagnostics.ChatTurnSpanScope;
import br.com.systec.hive.assistant.impl.diagnostics.SpanAttributeMapper;
import br.com.systec.hive.assistant.impl.service.LoggingChatTraceRecorder;
import br.com.systec.hive.integrationtest.support.StreamingITFixtures;
import br.com.systec.hive.provider.api.AiProvider;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.langchain4j.model.chat.response.PartialThinking;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** SEC-16 / NS-11: a unique thinking token and a secret Throwable never reach log, trace or spans. */
class StreamingCanaryIT {

    private static final String TOKEN = "CANARIO-THINKING-IT-6f0c2a";
    private static final String SECRET = "SENHA-DRIVER-IT-bb31";

    @Test
    void when_thinkingAndSecretFailure_then_onlyTheReasoningFrameCarriesTheToken() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            UUID session = UUID.randomUUID();
            List<TurnStreamEvent> events = new ArrayList<>();
            AtomicReference<dev.langchain4j.model.chat.response.StreamingChatResponseHandler> provider =
                    new AtomicReference<>();
            new LangChain4jTurnStreamGateway(
                    StreamingITFixtures.factory(provider, handler -> {
                        handler.onPartialThinking(new PartialThinking(TOKEN));
                        handler.onPartialResponse("answer");
                        handler.onError(new IllegalStateException("driver says " + SECRET + " / " + TOKEN));
                    }), StreamingITFixtures.policy(),
                    new StreamingMetrics(), StreamingITFixtures.scheduler())
                    .stream(StreamingITFixtures.request(session), events::add);

            assertThat(events).hasSize(3);
            assertThat(((TurnStreamEvent.Reasoning) events.get(0)).text()).isEqualTo(TOKEN);
            TurnStreamEvent.Failed failed = (TurnStreamEvent.Failed) events.get(2);
            assertThat(failed.code()).doesNotContain(TOKEN, SECRET).isEqualTo("unexpected");

            // ChatTrace.failure gets the closed label, never the Throwable text.
            ChatTrace trace = ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "model-x")
                    .failure(failed.code()).build();
            assertThat(trace.failure()).doesNotContain(TOKEN, SECRET);
            new LoggingChatTraceRecorder().record(trace);
            assertThat(appender.list).allSatisfy(event ->
                    assertThat(event.getFormattedMessage()).doesNotContain(TOKEN, SECRET));

            // The span child is built from the same Throwable; only the closed code is exported.
            List<ChatSpan> spans = new ArrayList<>();
            ChatSpanSink sink = spans::addAll;
            ChatTurnSpanScope.open(session, trace.promptTemplateId(), AiProvider.OPENAI, "model-x", sink);
            ChatTurnSpanScope.attemptStarted();
            ChatTurnSpanScope.attemptFailed(failed.cause());
            ChatTurnSpanScope.close(trace);
            assertThat(spans).extracting(ChatSpan::name).contains(
                    SpanAttributeMapper.ROOT_NAME, SpanAttributeMapper.CHILD_NAME);
            assertThat(spans.toString()).doesNotContain(TOKEN, SECRET);
            ChatSpan child = spans.stream()
                    .filter(span -> span.name().equals(SpanAttributeMapper.CHILD_NAME))
                    .findFirst().orElseThrow();
            assertThat(child.attributes()).containsEntry(SpanAttributeMapper.KEY_STATUS_MESSAGE,
                    "IllegalStateException:unexpected");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
