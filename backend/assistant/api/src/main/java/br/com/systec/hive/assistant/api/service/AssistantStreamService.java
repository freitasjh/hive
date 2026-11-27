package br.com.systec.hive.assistant.api.service;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.assistant.api.domain.ChatRequest;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Streaming counterpart of {@link ChatService}: the same turn preflight, then events instead of a
 * single reply. The web layer owns the emitter and the named-event serializer.
 */
public interface AssistantStreamService {

    StreamingTurnHandle stream(ChatRequest request, UUID tenantId, UUID userId,
                               Consumer<TurnStreamEvent> sink);
}
