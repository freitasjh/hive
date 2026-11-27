package br.com.systec.hive.ai.api.port;

import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;

import java.util.function.Consumer;

/**
 * Streaming counterpart of {@link ChatTurnGateway}. Added beside it, never replacing it: the
 * synchronous contract and its callers stay untouched (ADR-001 D5).
 */
public interface TurnStreamGateway {

    StreamingTurnHandle stream(TurnRequest request, Consumer<TurnStreamEvent> sink);
}
