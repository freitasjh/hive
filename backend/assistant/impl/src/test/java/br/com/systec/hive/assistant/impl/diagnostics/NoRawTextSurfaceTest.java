package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.provider.api.AiProvider;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RED-10: nenhuma superfície proibida recebe texto bruto — nem log, nem atributo extra. */
class NoRawTextSurfaceTest {

    @Test
    void when_fullFlowRuns_then_noRawLiteralReachesLogOrAnyOtherAttribute() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        List<List<ChatSpan>> published = new ArrayList<>();
        try {
            UUID session = UUID.randomUUID();
            UUID template = UUID.randomUUID();
            ChatTurnSpanScope.open(session, template, AiProvider.OPENAI, "gpt-4o-mini", published::add);
            TextRedactor redactor = new TextRedactor();
            ChatTurnSpanScope.attemptStarted();
            redactor.captureInput(SensitiveFixture.RAW);
            redactor.captureOutput(SensitiveFixture.RAW);
            ChatTurnSpanScope.attemptSucceeded(1, 2, 3);
            ChatTurnSpanScope.close(ChatTrace.builder(session, template, AiProvider.OPENAI, "gpt-4o-mini").build());

            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            String serialized = published.toString();
            for (String literal : SensitiveFixture.LITERALS) {
                assertThat(logged).doesNotContain(literal);
                assertThat(serialized).doesNotContain(literal);
            }
            assertThat(logged).doesNotContain("sk-");
            for (ChatSpan span : published.getFirst()) {
                assertThat(span.attributes().keySet().stream()
                        .filter(key -> key.startsWith("langfuse.observation."))
                        .filter(key -> key.contains("input") || key.contains("output") || key.contains("prompt"))
                        .toList())
                        .isSubsetOf(SpanAttributeMapper.KEY_INPUT, SpanAttributeMapper.KEY_OUTPUT);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void when_failureMessageContainsRawText_then_itIsNotPropagatedOrLogged() {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TextRedactor redactor = new TextRedactor(text -> {
                throw new IllegalStateException(SensitiveFixture.RAW);
            });
            TextCarrier.start();
            redactor.captureInput(SensitiveFixture.RAW);
            redactor.captureOutput(SensitiveFixture.RAW);

            String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
            for (String literal : SensitiveFixture.LITERALS) {
                assertThat(logged).doesNotContain(literal);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
