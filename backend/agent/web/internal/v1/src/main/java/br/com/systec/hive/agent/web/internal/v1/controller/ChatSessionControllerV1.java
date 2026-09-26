package br.com.systec.hive.agent.web.internal.v1.controller;

import br.com.systec.hive.agent.api.domain.ChatMessage;
import br.com.systec.hive.agent.api.domain.ChatSession;
import br.com.systec.hive.agent.api.service.ChatMessageService;
import br.com.systec.hive.agent.api.service.ChatSessionService;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatMessageDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.ChatSessionDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.CreateSessionDTO;
import br.com.systec.hive.agent.web.internal.v1.dto.UpdateSessionDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.ChatMessageMapperDTO;
import br.com.systec.hive.agent.web.internal.v1.mapper.ChatSessionMapperDTO;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/chat/sessions")
@Tag(name = "Hive Agent - Chat Sessions", description = "CRUD de sessões de chat")
public class ChatSessionControllerV1 {

    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final ChatSessionMapperDTO sessionMapper;
    private final ChatMessageMapperDTO messageMapper;

    public ChatSessionControllerV1(ChatSessionService chatSessionService,
                                   ChatMessageService chatMessageService,
                                   ChatSessionMapperDTO sessionMapper,
                                   ChatMessageMapperDTO messageMapper) {
        this.chatSessionService = chatSessionService;
        this.chatMessageService = chatMessageService;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Cria nova sessão de chat")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<ChatSessionDTO> create(@RequestBody @Valid CreateSessionDTO payload) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        ChatSession created = chatSessionService.create(payload.agentId(), payload.title(), tenantId, userId);
        ChatSessionDTO dto = sessionMapper.toDto(created);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista sessões paginado por updateAt DESC")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<Page<ChatSessionDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > 50) {
            throw new IllegalArgumentException("size must be <= 50");
        }
        if (size <= 0) size = 20;
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updateAt"));
        Page<ChatSession> result = chatSessionService.listSessions(tenantId, userId, pageable);
        Page<ChatSessionDTO> dtoPage = result.map(sessionMapper::toDto);
        return RestControllerTool.of().buildSuccessResponse(dtoPage);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Busca sessão por ID anti-IDOR")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<ChatSessionDTO> get(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        ChatSession session = chatSessionService.getSession(id, tenantId, userId);
        return RestControllerTool.of().buildSuccessResponse(sessionMapper.toDto(session));
    }

    @GetMapping(value = "/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista mensagens da sessão ordenadas createAt ASC")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<Page<ChatMessageDTO>> messages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (size > 100) {
            throw new IllegalArgumentException("size must be <= 100");
        }
        if (size <= 0) size = 50;
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createAt"));
        Page<ChatMessage> result = chatMessageService.getMessages(id, tenantId, userId, pageable);
        Page<ChatMessageDTO> dtoPage = result.map(messageMapper::toDto);
        return RestControllerTool.of().buildSuccessResponse(dtoPage);
    }

    @DeleteMapping(value = "/{id}")
    @Operation(description = "Deleta sessão cascade")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        chatSessionService.deleteSession(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Atualiza título da sessão")
    @HasPermission("CHAT_SESSION_MANAGE")
    public ResponseEntity<ChatSessionDTO> update(@PathVariable UUID id, @RequestBody @Valid UpdateSessionDTO payload) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        ChatSession updated = chatSessionService.updateTitle(id, payload.title(), tenantId, userId);
        return RestControllerTool.of().buildSuccessResponse(sessionMapper.toDto(updated));
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
