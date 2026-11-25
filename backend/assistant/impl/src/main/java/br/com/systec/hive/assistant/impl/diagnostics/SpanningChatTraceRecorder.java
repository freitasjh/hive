package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.assistant.api.diagnostics.ChatTrace;
import br.com.systec.hive.assistant.api.diagnostics.ChatTraceRecorder;

/**
 * Compõe a porta existente sem mudar sua assinatura: fecha/enriquece o root no mesmo `record` que
 * sempre existiu e só depois entrega o `ChatTrace` ao adaptador de log original.
 */
public final class SpanningChatTraceRecorder implements ChatTraceRecorder {

    private final ChatTraceRecorder delegate;

    public SpanningChatTraceRecorder(ChatTraceRecorder delegate) {
        this.delegate = delegate == null ? ChatTraceRecorder.noop() : delegate;
    }

    @Override
    public void record(ChatTrace trace) {
        ChatTurnSpanScope.close(trace);
        delegate.record(trace);
    }
}
