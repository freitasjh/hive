package br.com.systec.hive.publicapi.web.internal.v1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import br.com.systec.hive.commons.api.tools.controller.RestPath;
import br.com.systec.hive.commons.api.tools.security.HasPermission;
import br.com.systec.hive.publicapi.api.exceptions.InvalidClientException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidGrantException;
import br.com.systec.hive.publicapi.api.exceptions.InvalidScopeException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotActiveException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.PromptNotInAllowlistException;
import br.com.systec.hive.publicapi.api.exceptions.PromptVersionNotFoundException;
import br.com.systec.hive.publicapi.api.exceptions.SystemRoleNotAllowedException;
import br.com.systec.hive.publicapi.api.exceptions.UnknownModelException;
import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.publicapi.api.service.PublicChatService;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionRequestDTO;
import br.com.systec.hive.publicapi.api.dto.ChatCompletionResponse;
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.api.dto.OpenAiError;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Public Chat", description = "Chat completions compatível com OpenAI para clientes públicos")
@RequestMapping(RestPath.V1 + "/public/v1/chat")
public class PublicChatControllerV1 {

    private final PublicChatService publicChatService;

    public PublicChatControllerV1(PublicChatService publicChatService) {
        this.publicChatService = publicChatService;
    }

    @SecurityRequirements({
            @SecurityRequirement(name = "bearer-key"),
            @SecurityRequirement(name = "apiKeyAuth")
    })
    @Operation(summary = "Cria uma chat completion",
            description = "Resposta com choices, usage e as extensões Hive prompt_code/metadata.hive. "
                    + "O duplo /v1 do path é preservado por compatibilidade com SDKs OpenAI.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "chat.completion"),
            @ApiResponse(responseCode = "400", description = "unknown_model | streaming_not_supported | system_role_not_allowed",
                    content = @Content(schema = @Schema(implementation = OpenAiError.class))),
            @ApiResponse(responseCode = "403", description = "prompt_not_in_allowlist"),
            @ApiResponse(responseCode = "404", description = "prompt_not_found"),
            @ApiResponse(responseCode = "409", description = "prompt_not_active | prompt_version_not_found"),
            @ApiResponse(responseCode = "429", description = "quota_exceeded (Retry-After)"),
            @ApiResponse(responseCode = "500", description = "internal_error")
    })
    @PostMapping(value = "/completions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @HasPermission("PUBLIC_CHAT_COMPLETE")
    public ResponseEntity<ChatCompletionResponse> completions(@RequestBody @Valid ChatCompletionRequestDTO body) {
        ChatCompletionResponse resp = publicChatService.complete(body);
        return ResponseEntity.ok().headers(measuredHeaders()).body(resp);
    }

    @ExceptionHandler({PromptNotInAllowlistException.class})
    public ResponseEntity<OpenAiError> handleAllowlist(PromptNotInAllowlistException ex) {
        return openAi(403, "prompt_not_in_allowlist", "permission_error");
    }

    @ExceptionHandler({PromptNotFoundException.class})
    public ResponseEntity<OpenAiError> handleNotFound(PromptNotFoundException ex) {
        return openAi(404, "prompt_not_found", "invalid_request_error");
    }

    @ExceptionHandler({PromptNotActiveException.class})
    public ResponseEntity<OpenAiError> handleNotActive(PromptNotActiveException ex) {
        return openAi(409, "prompt_not_active", "invalid_request_error");
    }

    @ExceptionHandler({PromptVersionNotFoundException.class})
    public ResponseEntity<OpenAiError> handleVersionNotFound(PromptVersionNotFoundException ex) {
        return openAi(409, "prompt_version_not_found", "invalid_request_error");
    }

    @ExceptionHandler({UnknownModelException.class})
    public ResponseEntity<OpenAiError> handleUnknownModel(UnknownModelException ex) {
        return openAi(400, "unknown_model", "invalid_request_error");
    }

    @ExceptionHandler({SystemRoleNotAllowedException.class})
    public ResponseEntity<OpenAiError> handleSystem(SystemRoleNotAllowedException ex) {
        return openAi(400, "system_role_not_allowed", "invalid_request_error");
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<OpenAiError> handleIllegal(IllegalArgumentException ex) {
        String msg = ex.getMessage();
        String code = "invalid_request";
        int status = 400;
        if ("stream_not_supported".equals(msg)) code = "streaming_not_supported";
        else if ("too_many_messages".equals(msg)) code = "too_many_messages";
        else if ("request_too_large".equals(msg)) code = "request_too_large";
        return openAi(status, code, "invalid_request_error");
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<OpenAiError> handleCatchAll(Exception ex) {
        return openAi(400, "invalid_request", "invalid_request_error");
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<OpenAiError> handleQuota(QuotaExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        br.com.systec.hive.publicapi.impl.quota.QuotaDecision decision = ex.getDecision();
        if (decision != null && decision.measured()) {
            addRateLimitHeaders(headers, decision.limit(), decision.remaining(), decision.resetEpochSeconds());
        }
        return ResponseEntity.status(429).headers(headers)
                .body(new OpenAiError(new OpenAiError.Error("quota_exceeded", "invalid_request_error", null, "quota_exceeded")));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<OpenAiError> handleBase(BaseException ex) {
        org.springframework.http.HttpStatus status = ex.getHttpStatus();
        int code = (status == null) ? 500 : status.value();
        String errCode = ex.getMessage() == null ? "error" : ex.getMessage();
        return ResponseEntity.status(code).body(new OpenAiError(new OpenAiError.Error(errCode, "invalid_request_error", null, errCode)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiError> handleGeneric(Exception ex) {
        return openAi(500, "internal_error", "server_error");
    }

    /** Headers só existem quando o guard mediu valores — fail-open nunca inventa limit/remaining. */
    private static HttpHeaders measuredHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            if (!(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttrs)) {
                return headers;
            }
            var request = servletAttrs.getRequest();
            Object limit = request.getAttribute("publicapi.quota.limit");
            Object remaining = request.getAttribute("publicapi.quota.remaining");
            Object reset = request.getAttribute("publicapi.quota.reset");
            if (limit instanceof Number l && remaining instanceof Number r && reset instanceof Number t) {
                addRateLimitHeaders(headers, l.intValue(), r.longValue(), t.longValue());
            }
        } catch (IllegalStateException ignored) {
            // sem request attributes: omitir headers
        }
        return headers;
    }

    private static void addRateLimitHeaders(HttpHeaders headers, int limit, long remaining, long resetEpoch) {
        headers.set("X-RateLimit-Limit-Requests", String.valueOf(limit));
        headers.set("X-RateLimit-Remaining-Requests", String.valueOf(remaining));
        headers.set("X-RateLimit-Reset-Requests", String.valueOf(resetEpoch));
    }

    private ResponseEntity<OpenAiError> openAi(int status, String code, String type) {
        OpenAiError.Error err = new OpenAiError.Error(code, type, null, code);
        return ResponseEntity.status(status).body(new OpenAiError(err));
    }
}