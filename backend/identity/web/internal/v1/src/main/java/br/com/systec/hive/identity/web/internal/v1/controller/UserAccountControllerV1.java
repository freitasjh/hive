package br.com.systec.hive.identity.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.exceptions.StandardError;
import br.com.systec.hive.commons.api.tools.exceptions.ValidationError;
import br.com.systec.hive.identity.api.domain.UserAccount;
import br.com.systec.hive.identity.api.service.UserAccountService;
import br.com.systec.hive.identity.web.internal.v1.dto.UserAccountCreateDTO;
import br.com.systec.hive.identity.web.internal.v1.mapper.UserAccountMapperDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(RestPath.V1 + "/user-accounts")
@Tag(name = "Hive Identity - Registro", description = "Criação de novas contas de acesso")
public class UserAccountControllerV1 {
    private final UserAccountService service;
    private final UserAccountMapperDTO mapper;

    public UserAccountControllerV1(UserAccountService service, UserAccountMapperDTO mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/create", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Retorna os dados para o profile do usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Não autorizado", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StandardError.class))
            }),
            @ApiResponse(responseCode = "400", description = "Erro de validação", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ValidationError.class))
            }),
            @ApiResponse(responseCode = "500", description = "Erro generico", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StandardError.class))
            })
    })
    public ResponseEntity<Void> create(@RequestBody @Valid UserAccountCreateDTO createDTO) {
        UserAccount userAccount = mapper.toDomain(createDTO);

        service.create(userAccount);

        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Lista contas de usuário (placeholder para não retornar 500)")
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(List.of());
    }
}
