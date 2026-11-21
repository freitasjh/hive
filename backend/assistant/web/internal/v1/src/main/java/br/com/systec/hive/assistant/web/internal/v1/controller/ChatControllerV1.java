package br.com.systec.hive.assistant.web.internal.v1.controller;

import br.com.systec.hive.assistant.api.domain.ChatRequest;
import br.com.systec.hive.assistant.api.domain.ChatResponse;
import br.com.systec.hive.assistant.api.service.ChatService;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatRequestDTO;
import br.com.systec.hive.assistant.web.internal.v1.dto.ChatResponseDTO;
import br.com.systec.hive.assistant.web.internal.v1.mapper.ChatMapper;
import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.identity.api.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/chat")
@Tag(name = "Hive Chat", description = "Interação em tempo real com o assistente")
public class ChatControllerV1 {

    private final ChatService service;
    private final ChatMapper mapper;

    public ChatControllerV1(ChatService service, ChatMapper mapper) {
        this.service = service;
        this.mapper = mapper;
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
