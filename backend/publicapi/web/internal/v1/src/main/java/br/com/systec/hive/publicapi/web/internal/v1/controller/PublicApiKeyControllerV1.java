package br.com.systec.hive.publicapi.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.publicapi.api.dto.ApiKeyCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeyRotatedDTO;
import br.com.systec.hive.publicapi.api.dto.ApiKeySummaryDTO;
import br.com.systec.hive.publicapi.api.service.PublicApiKeyAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ADR-002: gestão da key complementar na chain humana, fora de /v1/public/** e /manager/**. */
@RestController
@Tag(name = "Public API Keys", description = "Ciclo de vida da API key Bearer sk-")
@RequestMapping(RestPath.V1 + "/public-api/clients")
public class PublicApiKeyControllerV1 {

    private final PublicApiKeyAdminService keyService;

    public PublicApiKeyControllerV1(PublicApiKeyAdminService keyService) {
        this.keyService = keyService;
    }

    @PostMapping(value = "/{clientId}/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Emite key e devolve o plaintext uma única vez")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<ApiKeyCreatedDTO> issue(@PathVariable String clientId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(keyService.issue(clientId));
    }

    @GetMapping(value = "/{clientId}/keys", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista metadados das keys — sem plaintext nem hash")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<List<ApiKeySummaryDTO>> list(@PathVariable String clientId) {
        return ResponseEntity.ok(keyService.list(clientId));
    }

    @PostMapping(value = "/{clientId}/keys/{keyId}/rotate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotaciona a key e devolve o novo plaintext uma única vez")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<ApiKeyRotatedDTO> rotate(@PathVariable String clientId, @PathVariable String keyId) {
        return ResponseEntity.ok(keyService.rotate(clientId, keyId));
    }

    @PostMapping(value = "/{clientId}/keys/{keyId}/revoke")
    @Operation(summary = "Revoga a key com efeito imediato (Redis + DB)")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<Void> revoke(@PathVariable String clientId, @PathVariable String keyId) {
        keyService.revoke(clientId, keyId);
        return ResponseEntity.noContent().build();
    }
}
