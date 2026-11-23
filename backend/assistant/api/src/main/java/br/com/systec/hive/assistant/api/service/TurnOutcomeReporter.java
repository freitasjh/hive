package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.assistant.api.domain.PromptTemplate;
import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.provider.api.AiProvider;

import java.util.List;
import java.util.UUID;

/**
 * ADR-005: porta do desfecho do turno — a mesma assinatura de {@code TurnOutcomeMapper.resolve},
 * incluindo {@code turnId}/{@code clientId} da ADR-002. Implementação única em {@code assistant/impl}.
 */
public interface TurnOutcomeReporter {

    void resolve(UUID sessionId, UUID tenantId, UUID promptTemplateId, AiProvider provider,
                 String model, PromptTemplate promptTemplate, List<ChatMessage> history,
                 TurnReply reply, long elapsedMs, UUID turnId, String clientId);
}
