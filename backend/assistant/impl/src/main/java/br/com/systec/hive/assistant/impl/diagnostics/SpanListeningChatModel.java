package br.com.systec.hive.assistant.impl.diagnostics;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Envolve o cliente do provider para registrar o listener de spans sem alterar o cliente nem a
 * resposta. A chamada ao delegate continua no mesmo caminho síncrono de antes: os listeners do
 * próprio modelo disparam uma vez (dentro de `delegate.chat`) e o listener de telemetria uma vez.
 *
 * <p>Fonte da assinatura: `ChatModel.chat(ChatRequest)` / `ChatRequestOptions` no tag 1.19.0
 * (langchain4j-core, consulta 2026-11-25). `AiServices` chama `chat(ChatRequest)` via
 * `SynchronousChatExecutor`.
 */
public final class SpanListeningChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ChatModelListener listener;

    public SpanListeningChatModel(ChatModel delegate, ChatModelListener listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return notifyAround(chatRequest, () -> delegate.chat(chatRequest));
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest, ChatRequestOptions options) {
        return notifyAround(chatRequest, () -> delegate.chat(chatRequest, options));
    }

    private ChatResponse notifyAround(ChatRequest chatRequest, java.util.function.Supplier<ChatResponse> call) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ModelProvider provider = delegate.provider();
        listener.onRequest(new ChatModelRequestContext(chatRequest, provider, attributes));
        try {
            ChatResponse response = call.get();
            if (response != null) {
                listener.onResponse(new ChatModelResponseContext(response, chatRequest, provider, attributes));
            }
            return response;
        } catch (Exception error) {
            listener.onError(new ChatModelErrorContext(error, chatRequest, provider, attributes));
            throw error;
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
