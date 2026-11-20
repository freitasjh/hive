package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.domain.MfaErrorCode;
import br.com.systec.hive.security.api.domain.MfaSetupResult;
import br.com.systec.hive.security.api.exceptions.MfaException;
import br.com.systec.hive.security.api.service.MfaServicePort;
import br.com.systec.hive.security.web.internal.v1.dto.LoginResponseDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaChallengeDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaCodeDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaErrorResponseDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaPasswordDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaRecoveryDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaSetupResponseDTO;
import br.com.systec.hive.security.web.internal.v1.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoints de gerenciamento e segundo fator MFA, sob o prefixo já {@code permitAll} {@code /v1/auth/**}.
 *
 * <p><strong>Autenticação:</strong> {@code /setup}, {@code /enable} e {@code /disable} exigem
 * Bearer válido — userId/tenantId vêm do principal autenticado, nunca do corpo.
 * {@code /verify} e {@code /recovery} são chamados <em>sem</em> Bearer: o cliente deve usar
 * {@code omitAuthorization: true} (padrão de refresh/logout), pois o {@code SecurityFilter}
 * valida o Bearer antes do controller e rejeitaria um token expirado. Nestes, o tenant vem
 * de {@code X-Tenant-ID} com parsing seguro (igual ao logout).
 *
 * <p><strong>Contrato da Fase 3:</strong> verify/recovery devolvem 200 com o mesmo
 * {@link LoginResponseDTO} do login e o cookie de refresh — somente após código/challenge válidos.
 *
 * <p><strong>Sem logs:</strong> nenhum segredo, código, senha ou token é escrito em log.
 */
@RestController
@RequestMapping(RestPath.V1 + "/auth/mfa")
@Tag(name = "Identidade - MFA", description = "Configuração e segundo fator de MFA TOTP")
public class MfaControllerV1 {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    private final MfaServicePort service;

    public MfaControllerV1(MfaServicePort service) {
        this.service = service;
    }

    @PostMapping(value = "/setup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Gera secret, QR e 8 códigos de recuperação exibidos uma única vez. Exige Bearer.")
    public ResponseEntity<MfaSetupResponseDTO> setup() {
        CurrentUser current = requireAuthenticatedUser();
        MfaSetupResult result = service.setup(current.userId(), current.tenantId());
        return RestControllerTool.of().buildSuccessResponse(new MfaSetupResponseDTO(
                result.secret(), result.qrDataUri(), result.recoveryCodes()));
    }

    @PostMapping(value = "/enable", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Confirma o código TOTP e habilita o MFA. Exige Bearer.")
    public ResponseEntity<Void> enable(@RequestBody @Valid MfaCodeDTO payload) {
        CurrentUser current = requireAuthenticatedUser();
        service.enable(current.userId(), current.tenantId(), payload.code());
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @PostMapping(value = "/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Exige senha e remove secret + recovery codes. Exige Bearer.")
    public ResponseEntity<Void> disable(@RequestBody @Valid MfaPasswordDTO payload) {
        CurrentUser current = requireAuthenticatedUser();
        service.disable(current.userId(), current.tenantId(), payload.password());
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Valida o código, consome o challenge e emite o token. SEM Bearer (omitAuthorization).")
    public ResponseEntity<LoginResponseDTO> verify(@RequestHeader(value = TENANT_HEADER, required = false) String tenantIdHeader,
                                                   @RequestBody @Valid MfaChallengeDTO payload,
                                                   HttpServletResponse response) {
        return issue(service.verify(payload.challengeId(), parseTenantId(tenantIdHeader), payload.code()), response);
    }

    @PostMapping(value = "/recovery", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Consome recovery code de uso único e emite o token. SEM Bearer (omitAuthorization).")
    public ResponseEntity<LoginResponseDTO> recovery(@RequestHeader(value = TENANT_HEADER, required = false) String tenantIdHeader,
                                                     @RequestBody @Valid MfaRecoveryDTO payload,
                                                     HttpServletResponse response) {
        return issue(service.recovery(payload.challengeId(), parseTenantId(tenantIdHeader), payload.recoveryCode()), response);
    }

    /** Mesmo contrato do login: {userId, accessToken, type} + Set-Cookie refreshToken. */
    private ResponseEntity<LoginResponseDTO> issue(LoginAuthenticate authenticate, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.of().createRefreshToken(authenticate).toString());
        return RestControllerTool.of().buildSuccessResponse(
                new LoginResponseDTO(authenticate.getUserId(), authenticate.getAccessToken(), authenticate.getType()));
    }

    /** Erro semântico: status e nome vêm de {@link MfaErrorCode}; sem payload de credencial. */
    @ExceptionHandler(MfaException.class)
    public ResponseEntity<MfaErrorResponseDTO> handleMfaException(MfaException e) {
        return ResponseEntity.status(e.code().httpStatus())
                .body(new MfaErrorResponseDTO(e.code().name(), e.getMessage()));
    }

    private CurrentUser requireAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            throw new MfaException(MfaErrorCode.MFA_REQUIRED);
        }
        return new CurrentUser(user.getId(), user.getTenantId());
    }

    /** Parsing seguro: header ausente/malformado devolve null (não 400), igual ao logout. */
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

    private record CurrentUser(UUID userId, UUID tenantId) {
    }
}
