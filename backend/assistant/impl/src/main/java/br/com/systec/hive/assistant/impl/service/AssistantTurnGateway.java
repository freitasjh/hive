package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.assistant.api.domain.InferenceParams;
import br.com.systec.hive.assistant.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.domain.TurnRequest;
import br.com.systec.hive.assistant.impl.ai.AssistantDefault;
import br.com.systec.hive.assistant.impl.util.ProviderMessages;
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

    private final LlmClientFactory<ChatModel> llmClientFactory;
    private final LlmRetryPolicy retryPolicy;

    @Autowired
    public AssistantTurnGateway(LlmClientFactory<ChatModel> llmClientFactory) {
        this(llmClientFactory, LlmRetryPolicy.defaults());
    }

    /** Construtor de teste: política de retry sem espera. A abstrata cria o store em memória. */
    AssistantTurnGateway(LlmClientFactory<ChatModel> llmClientFactory, LlmRetryPolicy retryPolicy) {
        super();
        this.llmClientFactory = llmClientFactory;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public TurnReply execute(TurnRequest request) {
        // Fora do try, como no gateway anterior: um defeito na montagem não é falha do provider.
        InferenceParams params = request.promptTemplate().getInferenceParams() != null
                ? request.promptTemplate().getInferenceParams()
                : new InferenceParams();
        String system = systemFor(request.promptTemplate());
        String user = userFor(request);
        List<ChatMessage> prior = priorHistoryFor(request);
        List<ChatMessage> prompt = telemetryPrompt(system, prior, user);
        int promptChars = ProviderMessages.totalChars(prompt);
        int promptMessages = prompt.size();

        try {
            // Gate R2: createClientWithSession não tolera sessionId nulo. No fluxo público o campo
            // sessionId do TurnRequest carrega o UUID sintético por requisição; nenhuma coerção aqui.
            ChatModel client = llmClientFactory.createClientWithSession(request.provider(), request.model(),
                    params.getTemperature(), params.getMaxTokens(),
                    params.getTopP(), request.apiKey(), request.baseUrl(), request.sessionId());
            AssistantDefault proxy = proxyFor(client);

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
