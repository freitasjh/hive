package br.com.systec.hive.publicapi.web.internal.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidGrantException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidScopeException;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.impl.quota.RedisQuotaGuard;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import br.com.systec.hive.publicapi.web.internal.v1.dto.TokenRequestDTO;
import br.com.systec.hive.publicapi.web.internal.v1.dto.TokenResponseDTO;
import br.com.systec.hive.publicapi.api.dto.OpenAiError;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@Tag(name = "Public Auth", description = "OAuth client_credentials para a API pública do Hive")
@RequestMapping(RestPath.V1 + "/public/auth")
public class PublicAuthControllerV1 {

    private final PublicClientTokenIssuer tokenIssuer;
    private final RedisQuotaGuard quotaGuard;

    public PublicAuthControllerV1(PublicClientTokenIssuer tokenIssuer, RedisQuotaGuard quotaGuard) {
        this.tokenIssuer = tokenIssuer;
        this.quotaGuard = quotaGuard;
    }

    @Operation(summary = "Emite token client_credentials (JSON)",
            description = "Basic preferido; corpo legado aceito. Erros locais: 400 invalid_request, 401 invalid_client e 429 quota_exceeded (Retry-After, ADR-004).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido; Cache-Control: no-store, Pragma: no-cache"),
            @ApiResponse(responseCode = "400", description = "invalid_request",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class))),
            @ApiResponse(responseCode = "401", description = "invalid_client",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class))),
            @ApiResponse(responseCode = "429", description = "quota_exceeded (Retry-After)",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class)))
    })
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponseDTO> token(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                  @RequestBody @Valid TokenRequestDTO body) {
        return doToken(authorization, body);
    }

    @Operation(summary = "Emite token client_credentials (form-urlencoded)",
            description = "Mesmo contrato da variante JSON. Erros locais: 400 invalid_request, 401 invalid_client e 429 quota_exceeded (ADR-004).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido; Cache-Control: no-store, Pragma: no-cache"),
            @ApiResponse(responseCode = "400", description = "invalid_request",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class))),
            @ApiResponse(responseCode = "401", description = "invalid_client",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class))),
            @ApiResponse(responseCode = "429", description = "quota_exceeded (Retry-After)",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class)))
    })
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponseDTO> tokenForm(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                      @org.springframework.web.bind.annotation.ModelAttribute @Valid TokenRequestDTO body) {
        return doToken(authorization, body);
    }

    private ResponseEntity<TokenResponseDTO> doToken(String authorization, TokenRequestDTO body) {
        if (body == null || body.getGrant_type() == null || !"client_credentials".equals(body.getGrant_type())) {
            throw new InvalidGrantException();
        }

        String clientId = body.getClient_id();
        String rawSecret = body.getClient_secret();

        // client_secret_basic preferred
        if (authorization != null && authorization.toLowerCase().startsWith("basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
                int idx = decoded.indexOf(':');
                if (idx >= 0) {
                    clientId = decoded.substring(0, idx);
                    rawSecret = decoded.substring(idx + 1);
                }
            } catch (IllegalArgumentException e) {
                throw new InvalidClientException();
            }
        }

        if (clientId == null || rawSecret == null) {
            throw new InvalidClientException();
        }

        // rate limit (clientId + IP + global)
        String rlClient = clientId.isBlank() ? "unknown" : clientId;
        String ip = resolveClientIp();
        quotaGuard.checkTokenEndpoint(rlClient, ip, 30, 120, 30);

        String token = tokenIssuer.issueToken(clientId, rawSecret, body.getScope());

        long expiresIn = tokenIssuer.expiresInSeconds(token);

        // granted (intersected) scope for response
        String scope = tokenIssuer.grantedScope(clientId, body.getScope());

        TokenResponseDTO resp = new TokenResponseDTO(token, expiresIn, scope);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(resp);
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            jakarta.servlet.http.HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (IllegalStateException e) {
            return "unknown";
        }
    }

    @ExceptionHandler(InvalidClientException.class)
    public ResponseEntity<OpenAiError> handleInvalidClient(InvalidClientException ex) {
        OpenAiError.Error err = new OpenAiError.Error("Invalid client credentials", "invalid_request_error", null, "invalid_client");
        return ResponseEntity.status(401)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(new OpenAiError(err));
    }

    @ExceptionHandler(InvalidGrantException.class)
    public ResponseEntity<OpenAiError> handleInvalidGrant(InvalidGrantException ex) {
        OpenAiError.Error err = new OpenAiError.Error("grant_type must be client_credentials", "invalid_request_error", null, "invalid_grant");
        return ResponseEntity.status(400)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(new OpenAiError(err));
    }

    /**
     * ADR-004/QT04: superscreve o 400 invalid_request que a ADR-001 registrava como comportamento
     * real. Handler local tem precedência sobre o advice global (problem+json). Limites 30/120/30
     * permanecem fixos e sem agregação — apenas Retry-After, sem headers X-RateLimit-*.
     */
    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<OpenAiError> handleQuota(QuotaExceededException ex) {
        OpenAiError.Error err = new OpenAiError.Error("quota_exceeded", "invalid_request_error", null, "quota_exceeded");
        return ResponseEntity.status(429)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new OpenAiError(err));
    }

    @ExceptionHandler(InvalidScopeException.class)
    public ResponseEntity<OpenAiError> handleInvalidScope(InvalidScopeException ex) {
        OpenAiError.Error err = new OpenAiError.Error("Requested scope not granted", "invalid_request_error", null, "invalid_scope");
        return ResponseEntity.status(400)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(new OpenAiError(err));
    }

    // catch-all
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<OpenAiError> handleCatchAll(Exception ex) {
        OpenAiError.Error err = new OpenAiError.Error("Bad request", "invalid_request_error", null, "invalid_request");
        // map status
        int status = 400;
        if (ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException) {
            status = 405;
        }
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(new OpenAiError(err));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiError> handleGeneric(Exception ex) {
        OpenAiError.Error err = new OpenAiError.Error("Bad request", "invalid_request_error", null, "invalid_request");
        return ResponseEntity.status(400)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Pragma", "no-cache")
                .body(new OpenAiError(err));
    }
}
