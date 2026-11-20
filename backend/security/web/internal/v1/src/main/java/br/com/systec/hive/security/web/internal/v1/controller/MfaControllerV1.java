package br.com.systec.hive.security.web.internal.v1.controller;

import br.com.systec.hive.commons.api.tools.controller.RestControllerTool;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.security.api.domain.MfaErrorCode;
import br.com.systec.hive.security.api.domain.MfaSetupResult;
import br.com.systec.hive.security.api.exceptions.MfaException;
import br.com.systec.hive.security.api.service.MfaServicePort;
import br.com.systec.hive.security.web.internal.v1.dto.MfaChallengeDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaCodeDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaErrorResponseDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaPasswordDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaRecoveryDTO;
import br.com.systec.hive.security.web.internal.v1.dto.MfaSetupResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Endpoints de gerenciamento MFA (Fase 2), sob o prefixo já {@code permitAll} {@code /v1/auth/**}.
 *
 * <p><strong>Autenticação:</strong> {@code /setup}, {@code /enable} e {@code /disable} exigem
 * Bearer válido — userId/tenantId vêm do principal autenticado, nunca do corpo.
 * {@code /verify} e {@code /recovery} são chamados <em>sem</em> Bearer: o cliente deve usar
 * {@code omitAuthorization: true} (padrão de refresh/logout), pois o {@code SecurityFilter}
 * valida o Bearer antes do controller e rejeitaria um token expirado. Nestes, o tenant vem
 * de {@code X-Tenant-ID} com parsing seguro (igual ao logout).
 *
 * <p><strong>Contrato da Fase 2:</strong> verify/recovery devolvem 204 — nenhum access token
 * é emitido aqui. A emissão entra na Fase 3, integrada ao login.
 *
 * <p><strong>Sem logs:</strong> nenhum segredo, código, senha ou token é escrito em log.
 */
@RestController
@RequestMapping(RestPath.V1 + "/auth/mfa")
@Tag(name = "Identidade - MFA", description = "Configuração e desafio de MFA TOTP (Fase 2)")
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

    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Valida e consome o challenge. SEM Bearer (omitAuthorization). Fase 2: 204, sem token.")
    public ResponseEntity<Void> verify(@RequestHeader(value = TENANT_HEADER, required = false) String tenantIdHeader,
                                       @RequestBody @Valid MfaChallengeDTO payload) {
        service.verify(payload.challengeId(), parseTenantId(tenantIdHeader), payload.code());
        return RestControllerTool.of().buildSuccessResponseNoContent();
    }

    @PostMapping(value = "/recovery", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Consome recovery code de uso único. SEM Bearer (omitAuthorization). Fase 2: 204, sem token.")
    public ResponseEntity<Void> recovery(@RequestHeader(value = TENANT_HEADER, required = false) String tenantIdHeader,
                                         @RequestBody @Valid MfaRecoveryDTO payload) {
        service.recovery(payload.challengeId(), parseTenantId(tenantIdHeader), payload.recoveryCode());
        return RestControllerTool.of().buildSuccessResponseNoContent();
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
