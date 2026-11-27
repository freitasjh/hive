package br.com.systec.hive.publicapi.impl.service;

import br.com.systec.hive.ai.api.domain.TurnReply;
import br.com.systec.hive.ai.api.domain.TurnRequest;
import br.com.systec.hive.ai.api.port.ChatTurnGateway;
import br.com.systec.hive.assistant.api.domain.TurnPrompts;
import br.com.systec.hive.assistant.api.service.TurnOutcomeReporter;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionChoice;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionMessage;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionMetadata;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionResponse;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionUsage;
import br.com.systec.hive.publicapi.api.dto.HiveMetadata;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PublicChatServiceImpl implements PublicChatService {

    private final PublicChatPreflight preflight;
    private final ChatTurnGateway turnGateway;
    private final TurnOutcomeReporter turnOutcomeMapper;

    public PublicChatServiceImpl(PublicChatPreflight preflight,
                                 ChatTurnGateway turnGateway,
                                 TurnOutcomeReporter turnOutcomeMapper) {
        this.preflight = preflight;
        this.turnGateway = turnGateway;
        this.turnOutcomeMapper = turnOutcomeMapper;
    }

    @Override
    public ChatCompletionResponse complete(ChatCompletionRequestDTO request) {
        if (Boolean.TRUE.equals(request.getStream())) {
            throw new IllegalArgumentException("stream_not_supported");
        }
        PublicChatPreflight.Plan plan = preflight.of(request);

        long started = System.nanoTime();
        // Sync não herda o teto do stream: limited only by the prompt's own policy (CR-03).
        TurnReply reply = turnGateway.execute(new TurnRequest(plan.turnId(),
                br.com.systec.hive.assistant.api.domain.TurnPrompts.from(plan.prompt()),
                plan.resolved().provider(), plan.resolved().model(), plan.resolved().apiKey(),
                plan.resolved().baseUrl(), plan.conversation()));
        long elapsed = (System.nanoTime() - started) / 1_000_000L;

        turnOutcomeMapper.resolve(null, plan.tenantId(), plan.prompt().getId(),
                plan.resolved().provider(), plan.resolved().model(), plan.prompt(), List.of(),
                reply, elapsed, plan.turnId(), plan.clientId());

        String text = reply.text();
        ChatCompletionUsage usage = new ChatCompletionUsage(
                reply.promptTokens(), reply.completionTokens(), reply.totalTokens());
        ChatCompletionMessage message = new ChatCompletionMessage("assistant", text);
        ChatCompletionChoice choice = new ChatCompletionChoice(
                0, message, reply.finishReason() == null ? "stop" : reply.finishReason().toLowerCase());
        HiveMetadata hive = new HiveMetadata(
                plan.turnId().toString(), plan.prompt().getCode(), plan.prompt().getVersion(),
                plan.fingerprint(), plan.pinned() ? "PINNED" : "FOLLOW_ACTIVE",
                plan.providerName(), plan.resolved().model(), elapsed,
                plan.tenantId().toString());

        return new ChatCompletionResponse(
                "chatcmpl-" + plan.turnId().toString().substring(0, 8),
                "chat.completion", Instant.now().getEpochSecond(), plan.resolved().model(),
                List.of(choice), usage, plan.prompt().getCode(), new ChatCompletionMetadata(hive));
    }
}
