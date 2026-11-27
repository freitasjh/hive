package br.com.systec.hive.assistant.web.internal.v1.controller;

import br.com.systec.hive.ai.api.domain.TurnStreamEvent;
import br.com.systec.hive.ai.api.port.StreamingTurnHandle;
import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.exceptions.ChatRateLimitException;
import br.com.systec.hive.assistant.api.service.AssistantStreamService;
import br.com.systec.hive.assistant.api.service.ChatService;
import br.com.systec.hive.assistant.api.service.StreamAdmission;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatRequestDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatResponseDTO;
import br.com.systec.hive.assistant.web.internal.v1.mapper.ChatMapper;
import br.com.systec.hive.assistant.web.internal.v1.serializer.AssistantSseSerializer;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@RestController
@RequestMapping(RestPath.V1 + "/chat")
@Tag(name = "Hive Chat", description = "Interação em tempo real com o assistente")
public class ChatControllerV1 {

    private final ChatService service;
    private final ChatMapper mapper;
    private final AssistantStreamService streamService;
    private final StreamAdmission admission;

    public ChatControllerV1(ChatService service, ChatMapper mapper,
                            AssistantStreamService streamService, StreamAdmission admission) {
        this.service = service;
        this.mapper = mapper;
        this.streamService = streamService;
        this.admission = admission;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Envia uma mensagem para o assistente e retorna a resposta")
    @HasPermission("CHAT_SEND")
    public ResponseEntity<ChatResponseDTO> chat(@RequestBody @Valid ChatRequestDTO payload) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        ChatRequest chatRequest = mapper.toDomain(payload);
        ChatResponse response = service.chat(chatRequest, tenantId, userId);
        ChatResponseDTO dto = mapper.toResponse(response);
        return RestControllerTool.of().buildSuccessResponse(dto);
    }

    @PostMapping(value = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "text/event-stream")
    @Operation(description = "Streaming do turno com eventos nomeados thinking/text/done/error")
    @HasPermission("CHAT_SEND")
    public ResponseEntity<SseEmitter> stream(@RequestBody @Valid ChatRequestDTO payload) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        Object admissionToken = admission.tryAdmit(tenantId, String.valueOf(userId));
        if (admissionToken == null) {
            throw new ChatRateLimitException();
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean terminal = new AtomicBoolean();
        AtomicInteger frames = new AtomicInteger();
        AtomicReference<StreamingTurnHandle> handle = new AtomicReference<>();
        AtomicBoolean cancelRequested = new AtomicBoolean();

        Runnable release = () -> admission.release(tenantId, String.valueOf(userId), admissionToken);
        Runnable finish = () -> {
            if (terminal.compareAndSet(false, true)) {
                emitter.complete();
                release.run();
            }
        };
        Consumer<TurnStreamEvent> sink = event -> {
            if (terminal.get()) {
                return;
            }
            if (frames.incrementAndGet() > 2000) {
                // O teto é uma degradação controlada: ainda fecha com error → done → complete().
                try {
                    emitter.send(SseEmitter.event().name("error").data(AssistantSseSerializer.error(
                            new TurnStreamEvent.Failed("frame_limit", null)).data()));
                    emitter.send(SseEmitter.event().name("done")
                            .data(AssistantSseSerializer.doneWithoutUsage().data()));
                } catch (Exception ignored) {
                    // cliente já foi embora
                }
                finish.run();
                return;
            }
            try {
                AssistantSseSerializer.NamedEvent named = switch (event) {
                    case TurnStreamEvent.Reasoning reasoning -> AssistantSseSerializer.thinking(reasoning);
                    case TurnStreamEvent.Text text -> AssistantSseSerializer.text(text);
                    case TurnStreamEvent.Done done -> AssistantSseSerializer.done(done);
                    case TurnStreamEvent.Failed failed -> AssistantSseSerializer.error(failed);
                };
                emitter.send(SseEmitter.event().name(named.name()).data(named.data()));
                if (event instanceof TurnStreamEvent.Done) {
                    finish.run();
                } else if (event instanceof TurnStreamEvent.Failed) {
                    // error → done → complete(); um único terminal para o cliente.
                    emitter.send(SseEmitter.event().name("done")
                            .data(AssistantSseSerializer.doneWithoutUsage().data()));
                    finish.run();
                }
            } catch (Exception ignored) {
                finish.run();
            }
        };

        StreamingTurnHandle started;
        try {
            started = streamService.stream(mapper.toDomain(payload), tenantId, userId, sink);
        } catch (RuntimeException failure) {
            release.run();
            throw failure;
        }
        handle.set(started);
        if (cancelRequested.get()) {
            started.cancel();
        }
        Runnable cancel = () -> {
            if (!terminal.compareAndSet(false, true)) {
                return; // callback tardio depois do finish: não libera nem cancela de novo
            }
            cancelRequested.set(true);
            StreamingTurnHandle current = handle.get();
            if (current != null) {
                current.cancel();
            }
            release.run();
        };
        emitter.onCompletion(cancel::run);
        emitter.onError(ignored -> cancel.run());
        emitter.onTimeout(cancel::run);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-RateLimit-Limit-Requests",
                String.valueOf(br.com.systec.hive.assistant.api.service.ChatRateLimits.MAX_CALLS));
        return ResponseEntity.ok().headers(headers).body(emitter);
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new org.springframework.security.access.AccessDeniedException("Access denied: no authentication");
    }

    private UUID currentTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getTenantId() != null) return user.getTenantId();
        }
        try {
            return TenantContext.getCurrentTenant();
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant not found");
        }
    }
}
