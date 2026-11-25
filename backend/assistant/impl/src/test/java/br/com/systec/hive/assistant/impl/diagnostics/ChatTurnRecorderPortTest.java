package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;
import br.com.systec.hive.provider.api.AiProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTurnRecorderPortTest {

    @Test
    void when_thePortIsInspected_then_recordStillTakesExactlyOneChatTrace() throws Exception {
        Method record = ChatTraceRecorder.class.getDeclaredMethod("record", ChatTrace.class);
        assertThat(record.getParameterCount()).isEqualTo(1);
        assertThat(Arrays.stream(ChatTraceRecorder.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic() && !Modifier.isStatic(method.getModifiers()))
                .toList()).hasSize(1);
    }

    @Test
    void when_recordIsCalled_then_theRootIsPublishedInTheSameCallAndTheDelegateStillReceivesTheTrace() {
        List<List<ChatSpan>> published = new ArrayList<>();
        List<ChatTrace> recorded = new ArrayList<>();
        ChatTraceRecorder delegate = recorded::add;
        UUID session = UUID.randomUUID();
        ChatTrace trace = ChatTrace.builder(session, UUID.randomUUID(), AiProvider.OPENAI, "m").build();

        ChatTurnSpanScope.open(session, trace.promptTemplateId(), AiProvider.OPENAI, "m", published::add);
        new SpanningChatTraceRecorder(delegate).record(trace);

        assertThat(recorded).containsExactly(trace);
        assertThat(published).hasSize(1);
        assertThat(published.get(0)).hasSize(1);
        assertThat(published.get(0).get(0).name()).isEqualTo("chat.turn");
    }

    @Test
    void when_recordIsCalledTwiceInOneTurn_then_onlyOneRootIsPublished() {
        List<List<ChatSpan>> published = new ArrayList<>();
        ChatTrace trace = ChatTrace.builder(UUID.randomUUID(), UUID.randomUUID(), AiProvider.OPENAI, "m").build();

        ChatTurnSpanScope.open(trace.sessionId(), trace.promptTemplateId(), AiProvider.OPENAI, "m", published::add);
        ChatTraceRecorder recorder = new SpanningChatTraceRecorder(trace1 -> { });
        recorder.record(trace);
        recorder.record(trace);

        assertThat(published).hasSize(1);
    }
}
