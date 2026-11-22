package br.com.systec.hive.publicapi.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreatedDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreateDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientGrantsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPageDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPromptsDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientResponseDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientRotatedDTO;
import br.com.systec.hive.publicapi.api.service.PublicApiClientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * ADR-003: gestão administrativa de clients públicos.
 *
 * <p><b>Não</b> está sob {@code /v1/public/**} (chain M2M) nem {@code /manager/**} ({@code permitAll}).
 * Chain humana + {@code @HasPermission(AGENT_MANAGE)} em todas as operações. O tenant é o da sessão.
 */
@RestController
@Tag(name = "Public API Clients", description = "Gestão administrativa de clients da API pública")
@RequestMapping(RestPath.V1 + "/public-api/clients")
public class PublicApiClientControllerV1 {

    private final PublicApiClientAdminService adminService;

    public PublicApiClientControllerV1(PublicApiClientAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lista clients do tenant (page/size; padrão 20, teto 100)")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientPageDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page >= 0 e 1 <= size <= 100");
        }
        return ResponseEntity.ok(adminService.list(page, size));
    }

    @GetMapping(value = "/{clientId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Detalhe de um client — sem secret nem hash")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientResponseDTO> get(@PathVariable String clientId) {
        return ResponseEntity.ok(adminService.get(clientId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cria client e devolve o secret em plaintext uma única vez")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientCreatedDTO> create(@RequestBody @Valid PublicApiClientCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.create(request));
    }

    @PostMapping(value = "/{clientId}/rotate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotaciona o secret e devolve o plaintext uma única vez")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientRotatedDTO> rotate(@PathVariable String clientId) {
        return ResponseEntity.ok(adminService.rotate(clientId));
    }

    @PostMapping(value = "/{clientId}/revoke")
    @Operation(summary = "Revoga o client com efeito imediato (Redis + DB)")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<Void> revoke(@PathVariable String clientId) {
        adminService.revoke(clientId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/{clientId}/grants", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Substitui os grants do client")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientResponseDTO> updateGrants(
            @PathVariable String clientId, @RequestBody @Valid PublicApiClientGrantsDTO request) {
        return ResponseEntity.ok(adminService.updateGrants(clientId, request));
    }

    @PutMapping(value = "/{clientId}/prompts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Substitui os vínculos de prompt e pin do client")
    @HasPermission(SecurityConstants.AGENT_MANAGE)
    public ResponseEntity<PublicApiClientResponseDTO> updatePrompts(
            @PathVariable String clientId, @RequestBody @Valid PublicApiClientPromptsDTO request) {
        return ResponseEntity.ok(adminService.updatePrompts(clientId, request));
    }

}
