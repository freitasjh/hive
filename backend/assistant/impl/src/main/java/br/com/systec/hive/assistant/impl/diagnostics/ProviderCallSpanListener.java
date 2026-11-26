package br.com.systec.hive.assistant.impl.diagnostics;

import br.com.systec.hive.ai.impl.util.ProviderMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;

import java.lang.reflect.Method;

/**
 * Interface congelada do LangChain4j 1.19.0: uma filha por tentativa. O texto passa pelo redator
 * antes de entrar no portador de turno — o exportador (ADR-002) nunca vê o original.
 */
public final class ProviderCallSpanListener implements ChatModelListener {

    private final TextRedactor redactor;

    public ProviderCallSpanListener() {
        this(new TextRedactor());
    }

    public ProviderCallSpanListener(TextRedactor redactor) {
        this.redactor = redactor;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        ChatTurnSpanScope.attemptStarted();
        redactor.captureInput(promptOf(context));
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        if (context != null) {
            redactor.captureOutput(ProviderMessages.textOf(context.chatResponse()));
        }
        TokenUsage usage = context == null || context.chatResponse() == null
                ? null : context.chatResponse().tokenUsage();
        ChatTurnSpanScope.attemptSucceeded(
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount(),
                usage == null ? null : usage.totalTokenCount(),
                cachedTokensOf(usage));
    }

    /**
     *shortcut: reflexão evita dependência do SDK OpenAI no módulo; trocar por adaptador tipado se o cache virar contrato LangChain4j.
     */
    private static Integer cachedTokensOf(TokenUsage usage) {
        if (usage == null) {
            return null;
        }
        try {
            Method detailsMethod = usage.getClass().getMethod("inputTokensDetails");
            Object details = detailsMethod.invoke(usage);
            if (details == null) {
                return null;
            }
            Object cached = details.getClass().getMethod("cachedTokens").invoke(details);
            return cached instanceof Integer value ? value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        ChatTurnSpanScope.attemptFailed(context.error());
    }

    private static String promptOf(ChatModelRequestContext context) {
        if (context == null || context.chatRequest() == null) {
            return null;
        }
        StringBuilder prompt = new StringBuilder();
        for (ChatMessage message : context.chatRequest().messages()) {
            String text = ProviderMessages.textOf(message);
            if (text == null) {
                continue;
            }
            if (prompt.length() > 0) {
                prompt.append('\n');
            }
            prompt.append(text);
        }
        return prompt.length() == 0 ? null : prompt.toString();
    }
}
