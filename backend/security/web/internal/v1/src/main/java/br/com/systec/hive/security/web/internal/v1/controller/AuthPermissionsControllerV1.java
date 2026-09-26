package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.SecurityConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(RestPath.V1 + "/auth")
@Tag(name = "Identidade - Permissões", description = "Consulta de permissões do usuário autenticado")
public class AuthPermissionsControllerV1 {

    @GetMapping(value = "/permissions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Retorna permissões do usuário autenticado")
    public ResponseEntity<List<String>> permissions() {
        return ResponseEntity.ok(SecurityConstants.DEFAULT_PERMISSIONS);
    }
}
