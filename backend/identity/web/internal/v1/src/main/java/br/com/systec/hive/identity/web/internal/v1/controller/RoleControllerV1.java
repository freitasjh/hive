package br.com.systec.hive.identity.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestPath;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RestPath.V1 + "/roles")
@Tag(name = "Hive Identity - Roles", description = "Consulta de papéis")
public class RoleControllerV1 {

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista papéis disponíveis")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(List.of(
                Map.of("id", "ADMIN", "name", "ADMIN", "permissions", List.of("PROMPT_MANAGE", "AGENT_MANAGE", "CHAT_SEND", "CHAT_SESSION_MANAGE")),
                Map.of("id", "USER", "name", "USER", "permissions", List.of("CHAT_SEND", "CHAT_SESSION_MANAGE"))
        ));
    }
}
