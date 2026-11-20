package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.exceptions.StandardError;
import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.LoginResult;
import br.com.systec.hive.security.api.exceptions.RefreshTokenNotFoundException;
import br.com.systec.hive.security.api.service.LoginService;
import br.com.systec.hive.security.web.internal.v1.dto.LoginDTO;
import br.com.systec.hive.security.web.internal.v1.dto.LoginResponseDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaChallengeResponseDTO;
import br.com.systec.hive.security.web.internal.v1.dto.RefreshTokenResponseDTO;
import br.com.systec.hive.security.web.internal.v1.mapper.LoginMapperDTO;
import br.com.systec.hive.security.web.internal.v1.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(RestPath.V1 + "/auth")
@Tag(name = "Identidade - Autenticação", description = "Fluxos de login e renovação de token (JWT)")
public class LoginControllerV1 {
    private final LoginService service;
    private final LoginMapperDTO mapper;

    public LoginControllerV1(LoginService service, LoginMapperDTO mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Realiza a authenticação do usuario e retorna o Token de acesso")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retorna o token de acesso", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = LoginResponseDTO.class))
            }),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StandardError.class))
            }),
    })
    public ResponseEntity<?> login(@RequestBody @Valid LoginDTO payload, HttpServletResponse response) {
        Login login = mapper.toDomain(payload);

        LoginResult result = service.login(login);
        if (result instanceof LoginResult.MfaChallengeRequired challenge) {
            // Nenhum token e nenhum cookie de refresh no ramo de desafio (SPEC §4).
            return RestControllerTool.of().buildSuccessResponse(
                    new MfaChallengeResponseDTO(true, challenge.challengeId(), challenge.expiresIn()));
        }

        LoginAuthenticate loginAuthenticate = ((LoginResult.Success) result).authenticate();
        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.of().createRefreshToken(loginAuthenticate).toString());

        return RestControllerTool.of().buildSuccessResponse(mapper.toLoginResponse(loginAuthenticate));
    }

    @PostMapping(value = "/refresh-token", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Retorna o token de acesso", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RefreshTokenResponseDTO.class))
            }),
            @ApiResponse(responseCode = "401", description = "Não autorizado", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StandardError.class))
            }),
            @ApiResponse(responseCode = "500", description = "Erro generico", content = {
                    @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = StandardError.class))
            }),
    })
    public ResponseEntity<RefreshTokenResponseDTO> refreshToken(HttpServletResponse response){
        LoginAuthenticate loginAuthenticateVO = service.refreshToken();

        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.of().createRefreshToken(loginAuthenticateVO).toString());

        return RestControllerTool.of().buildSuccessResponse(new RefreshTokenResponseDTO(loginAuthenticateVO.getAccessToken()));
    }

    @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Encerra a sessão revogando o refresh token e expirando o cookie")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Logout realizado"),
    })
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
                                       @CookieValue(name = "refreshToken", required = false) String refreshToken,
                                       HttpServletResponse response) {
        UUID tenantId = parseTenantId(tenantIdHeader);
        if (tenantId != null && refreshToken != null && !refreshToken.isBlank()) {
            service.logout(tenantId, refreshToken);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.of().revokeRefreshToken().toString());
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    private static UUID parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
