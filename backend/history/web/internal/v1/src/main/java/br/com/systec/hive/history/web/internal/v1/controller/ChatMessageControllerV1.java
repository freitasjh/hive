package br.com.systec.hive.history.web.internal.v1.controller;

import br.com.systec.hive.history.api.domain.ChatMessage;
import br.com.systec.hive.history.api.service.ChatMessageService;
import br.com.systec.hive.history.web.internal.v1.dto.ChatMessageDTO;
import br.com.systec.hive.history.web.internal.v1.mapper.ChatMessageMapperDTO;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/chat/sessions")
@Tag(name = "Hive Chat Sessions", description = "CRUD de sessões de chat")
public class ChatMessageControllerV1 {

    private final ChatMessageService chatMessageService;
    private final ChatMessageMapperDTO messageMapper;

    public ChatMessageControllerV1(ChatMessageService chatMessageService,
                                   ChatMessageMapperDTO messageMapper) {
        this.chatMessageService = chatMessageService;
        this.messageMapper = messageMapper;
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

        if (size <= 0) {
            size = 50;
        }

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createAt"));

        Page<ChatMessage> result = chatMessageService.getMessages(id, tenantId, userId, pageable);
        Page<ChatMessageDTO> dtoPage = result.map(messageMapper::toDto);

        return RestControllerTool.of().buildSuccessResponse(dtoPage);
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
