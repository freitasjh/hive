package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.ai.api.AssistantDefault;
import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.impl.AbstractLangChain4jTurnGateway;
import br.com.systec.hive.ai.impl.util.ProviderMessages;
import br.com.systec.hive.assistant.impl.diagnostics.ChatSpanSink;
import br.com.systec.hive.assistant.impl.diagnostics.ChatTurnSpanScope;
import br.com.systec.hive.assistant.impl.diagnostics.ProviderCallSpanListener;
import br.com.systec.hive.assistant.impl.diagnostics.SpanListeningChatModel;
import br.com.systec.hive.provider.api.LlmClientFactory;
import br.com.systec.hive.provider.langchain4j.LlmRetryPolicy;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.Result;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A concreta do gateway (ADR-020 §5.2): retry, cliente por turno e métricas.
 *
 * <p>A montagem manual de mensagens e a chamada direta ao modelo foram substituídas pelo proxy
 * {@link AssistantDefault}: o system vem de {@code @SystemMessage}, o turno de {@code @UserMessage} e o
 * histórico da {@code ChatMemory} semeada pela abstrata. O que ficou aqui é a política que o builder
 * do AiService não deve conter — {@link LlmRetryPolicy} fora do proxy, cliente criado por turno e um
 * {@link TurnReply} total (falha é valor, nunca exceção).
 *
 * <p><b>Métricas</b>: os tokens e o {@code FinishReason} vêm de {@code Result<T>} — retorno do proxy,
 * com o {@code TokenUsage} agregado e o {@code FinishReason} do {@code ChatResponse} final
 * (Result.java, tag 1.19.0, 2026-11-21). Já {@code promptMessages}/{@code promptChars} são
 * reconstruídos aqui a partir de system + histórico + user, porque o proxy não os expõe. O teste de
 * caracterização (AI-01) fixou 4 mensagens / 59 chars e 120/340/460/STOP como baseline.
 */
@Component
public class AssistantTurnGateway extends AbstractLangChain4jTurnGateway {

    /**
     * Texto exato do seed V6 (`V6__seed_default_prompt.sql:65-67`), preservado byte a byte.
     * A V8 remove as linhas do banco; esta constante é o fallback obrigatório em código.
     * Política do assistant — por isso vive aqui e não em {@code ai/api} (ADR-021, ajuste 2).
     */
    public static final String PROMPT = "Você é um assistente útil e respeitoso. Responda sempre em português brasileiro, "
            + "de forma clara, objetiva e correta. Se não tiver certeza, diga que não sabe "
            + "em vez de inventar. Mantenha o tom profissional e cordial.";

    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final LlmRetryPolicy retryPolicy;
    private final ChatSpanSink spanSink;

    @Autowired
    public AssistantTurnGateway(LlmClientFactory<ChatModel> llmClientFactory) {
        this(llmClientFactory, LlmRetryPolicy.defaults(), ChatSpanSink.noop());
    }

    /** Construtor de teste: política de retry sem espera. A abstrata cria o store em memória. */
    AssistantTurnGateway(LlmClientFactory<ChatModel> llmClientFactory, LlmRetryPolicy retryPolicy) {
        this(llmClientFactory, retryPolicy, ChatSpanSink.noop());
    }

    /** Construtor de teste com sink de spans: a fatia de contrato não depende de exporter. */
    public AssistantTurnGateway(LlmClientFactory<ChatModel> llmClientFactory, LlmRetryPolicy retryPolicy,
                                ChatSpanSink spanSink) {
        super();
        this.llmClientFactory = llmClientFactory;
        this.retryPolicy = retryPolicy;
        this.spanSink = spanSink;
    }

    /**
     * Regra de precedência do assistant: texto do turno > {@link #PROMPT}.
     * Um texto em branco conta como ausente — {@code SystemMessage} vazio instrui o modelo a ser
     * breve, o que a conversa não pediu. Esta regra é política do assistant: fica aqui, nunca em
     * {@code ai/impl} (ADR-021, ajuste 3).
     */
    protected final String systemFor(String text) {
        return text == null || text.isBlank() ? PROMPT : text;
    }

    /** A última mensagem renderizada do turno é o {@code @UserMessage}; o resto é histórico. */
    protected final String userFor(TurnRequest request) {
        List<ChatMessage> conversation = request.conversation();
        if (conversation.isEmpty()) {
            return "";
        }
        String text = ProviderMessages.textOf(conversation.getLast());
        return text == null ? "" : text;
    }

    protected final List<ChatMessage> priorHistoryFor(TurnRequest request) {
        List<ChatMessage> conversation = request.conversation();
        if (conversation.size() <= 1) {
            return List.of();
        }
        return new ArrayList<>(conversation.subList(0, conversation.size() - 1));
    }

    @Override
    public TurnReply execute(TurnRequest request) {
        // ADR-001: root aberto na entrada do turno, antes de qualquer tentativa ao provider.
        ChatTurnSpanScope.open(request.sessionId(), request.promptTemplate().templateId(),
                request.provider(), request.model(), spanSink);
        // Fora do try, como no gateway anterior: um defeito na montagem não é falha do provider.
        String system = systemFor(request.promptTemplate().text());
        String user = userFor(request);
        List<ChatMessage> prior = priorHistoryFor(request);
        List<ChatMessage> prompt = telemetryPrompt(system, prior, user);
        int promptChars = ProviderMessages.totalChars(prompt);
        int promptMessages = prompt.size();

        try {
            // Gate R2: createClientWithSession não tolera sessionId nulo. No fluxo público o campo
            // sessionId do TurnRequest carrega o UUID sintético por requisição; nenhuma coerção aqui.
            ChatModel client = llmClientFactory.createClientWithSession(request.provider(), request.model(),
                    request.promptTemplate().temperature(), request.promptTemplate().maxTokens(),
                    request.promptTemplate().topP(), request.apiKey(), request.baseUrl(), request.sessionId());
            AssistantDefault proxy = proxyFor(new SpanListeningChatModel(client, new ProviderCallSpanListener()));

            // A8: uma chamada por vez por @MemoryId. Seed + proxy dentro do mesmo lock, senão dois
            // turnos poderiam reescrever a memória um do outro entre o set e a leitura do AiService.
            //
            // O seed fica DENTRO do retry: o AiService acrescenta system e user à ChatMemory a cada
            // chamada, então sem re-sementar antes de cada tentativa o 2º attempt enviaria o user
            // duplicado e as contagens reconstruídas acima não descreveriam o prompt real.
            synchronized (lockFor(request.sessionId())) {
                Result<String> result = retryPolicy.call(() -> {
                    seedMemory(request.sessionId(), prior);
                    return proxy.chat(request.sessionId(), system, user);
                });
                return toReply(result, promptMessages, promptChars);
            }
        } catch (Exception e) {
            // Exceção do provider sem wrapper: a classificação do chamador lê o nome da classe.
            return TurnReply.failed(promptMessages, promptChars, e);
        }
    }

    @Override
    protected AssistantDefault proxyFor(ChatModel client) {
        return AiServices.builder(AssistantDefault.class)
                .chatModel(client)
                .chatMemoryProvider(chatMemoryProvider())
                .build();
    }

    @Override
    protected TurnReply toReply(Result<String> result, int promptMessages, int promptChars) {
        String reply = result == null ? null : result.content();
        TokenUsage usage = result == null ? null : result.tokenUsage();
        FinishReason finishReason = result == null ? null : result.finishReason();
        Integer promptTokens = usage == null ? null : usage.inputTokenCount();
        Integer completionTokens = usage == null ? null : usage.outputTokenCount();
        Integer totalTokens = usage == null ? null : usage.totalTokenCount();
        String reason = finishReason == null ? null : finishReason.name();

        if (reply == null || reply.isBlank()) {
            // Reasoning models que gastam o orçamento em content=null (mimo) ou "" (glm) chegam aqui.
            return TurnReply.emptyResponse(promptMessages, promptChars,
                    promptTokens, completionTokens, totalTokens, reason);
        }
        return TurnReply.answered(reply, promptMessages, promptChars,
                promptTokens, completionTokens, totalTokens, reason);
    }
}
