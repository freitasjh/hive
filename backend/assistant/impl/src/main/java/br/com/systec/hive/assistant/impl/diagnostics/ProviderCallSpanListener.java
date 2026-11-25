package br.com.systec.hive.assistant.impl.diagnostics;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;

/** A interface congelada do LangChain4j 1.19.0 alimenta uma filha por tentativa. */
public final class ProviderCallSpanListener implements ChatModelListener {

    @Override
    public void onRequest(ChatModelRequestContext context) {
        ChatTurnSpanScope.attemptStarted();
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        TokenUsage usage = context.chatResponse().tokenUsage();
        ChatTurnSpanScope.attemptSucceeded(
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(),
                usage == null ? null : usage.totalTokenCount());
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        ChatTurnSpanScope.attemptFailed(context.error());
    }
}
