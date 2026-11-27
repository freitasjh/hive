package br.com.systec.hive.assistant.impl.service;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.ai.impl.util.ProviderMessages;
import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.api.service.AssistantStreamService;
import br.com.systec.hive.assistant.api.service.TurnOutcomeReporter;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Assistant SSE turn: the same preflight and the same trace/assistant-row as sync, then events
 * instead of a single reply (CR-05). Only text is accumulated — thinking is never retained or
 * persisted; a failure frame carries the closed code, never the provider's Throwable.
 */
@Service
public class AssistantStreamServiceImpl implements AssistantStreamService {

    private final ChatServiceImpl chatService;
    private final TurnStreamGateway turnStreamGateway;
    private final ChatAssistantTxService chatAssistantTxService;
    private final TurnOutcomeReporter turnOutcomeReporter;

    public AssistantStreamServiceImpl(ChatServiceImpl chatService, TurnStreamGateway turnStreamGateway,
                                      ChatAssistantTxService chatAssistantTxService,
                                      TurnOutcomeReporter turnOutcomeReporter) {
        this.chatService = Objects.requireNonNull(chatService, "chatService");
        this.turnStreamGateway = Objects.requireNonNull(turnStreamGateway, "turnStreamGateway");
        this.chatAssistantTxService = Objects.requireNonNull(chatAssistantTxService, "chatAssistantTxService");
        this.turnOutcomeReporter = Objects.requireNonNull(turnOutcomeReporter, "turnOutcomeReporter");
    }

    @Override
    public StreamingTurnHandle stream(ChatRequest request, UUID tenantId, UUID userId,
                                      Consumer<TurnStreamEvent> sink) {
        ChatServiceImpl.PreparedTurn prepared = chatService.prepare(request, tenantId, userId);
        long started = System.nanoTime();
        StringBuilder answer = new StringBuilder();
        AtomicBoolean settled = new AtomicBoolean();

        Consumer<TurnStreamEvent> tracked = event -> {
            if (event instanceof TurnStreamEvent.Text text) {
                answer.append(text.text());
            }
            // Telemetry never changes the wire: persist first, then hand the event to the controller.
            if (event instanceof TurnStreamEvent.Done done && settled.compareAndSet(false, true)) {
                try {
                    settle(prepared, tenantId, answer.toString(), done, elapsedMs(started));
                } catch (RuntimeException ignored) {
                    // A storage/telemetry failure must not turn an already-streamed turn into an error.
                }
            }
            // Só o caminho assistant carrega o sessionId no terminal; o gateway é compartilhado com o
            // endpoint público, cujo chunk final deve permanecer sem o campo (Emenda 3).
            sink.accept(event instanceof TurnStreamEvent.Done done
                    ? new TurnStreamEvent.Done(done.finishReason(), done.usage(), prepared.sessionId())
                    : event);
        };

        return turnStreamGateway.stream(new TurnRequest(prepared.sessionId(),
                TurnPrompts.from(prepared.promptTemplate()), prepared.resolved().provider(),
                prepared.resolved().model(), prepared.resolved().apiKey(), prepared.resolved().baseUrl(),
                prepared.conversation()), tracked);
    }

    private void settle(ChatServiceImpl.PreparedTurn prepared, UUID tenantId, String text,
                        TurnStreamEvent.Done done, long elapsedMs) {
        String body = text == null ? "" : text;
        Integer[] counts = TurnStreamEvent.tokenCounts(done);
        String reason = TurnStreamEvent.finishReasonName(done);
        TurnReply reply = TurnReply.answered(body, prepared.conversation().size(),
                ProviderMessages.totalChars(prepared.conversation()),
                counts == null ? null : counts[0], counts == null ? null : counts[1],
                counts == null ? null : counts[2], reason);

        turnOutcomeReporter.resolve(prepared.sessionId(), tenantId, prepared.promptTemplate().getId(),
                prepared.resolved().provider(), prepared.resolved().model(), prepared.promptTemplate(),
                prepared.history(), reply, elapsedMs, null, null);
        chatAssistantTxService.saveAssistantAndTouch(prepared.sessionId(), tenantId, body);
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }
}
