package br.com.systec.hive.publicapi.web.internal.v1.controller;

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
import br.com.systec.hive.publicapi.impl.quota.QuotaExceededException;
import br.com.systec.hive.publicapi.web.internal.v1.error.OpenAiErrorResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(RestPath.V1 + "/public/v1/chat")
public class PublicChatControllerV1 {

    private final PublicChatService publicChatService;

    public PublicChatControllerV1(PublicChatService publicChatService) {
        this.publicChatService = publicChatService;
    }

    @PostMapping(value = "/completions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @HasPermission("PUBLIC_CHAT_COMPLETE")
    public ResponseEntity<Object> completions(@RequestBody @Valid ChatCompletionRequestDTO body) {
        Object resp = publicChatService.complete(body);
        return ResponseEntity.ok(resp);
    }

    @ExceptionHandler({PromptNotInAllowlistException.class})
    public ResponseEntity<OpenAiErrorResponse> handleAllowlist(PromptNotInAllowlistException ex) {
        return openAi(403, "prompt_not_in_allowlist", "permission_error");
    }

    @ExceptionHandler({PromptNotFoundException.class})
    public ResponseEntity<OpenAiErrorResponse> handleNotFound(PromptNotFoundException ex) {
        return openAi(404, "prompt_not_found", "invalid_request_error");
    }

    @ExceptionHandler({PromptNotActiveException.class})
    public ResponseEntity<OpenAiErrorResponse> handleNotActive(PromptNotActiveException ex) {
        return openAi(409, "prompt_not_active", "invalid_request_error");
    }

    @ExceptionHandler({PromptVersionNotFoundException.class})
    public ResponseEntity<OpenAiErrorResponse> handleVersionNotFound(PromptVersionNotFoundException ex) {
        return openAi(409, "prompt_version_not_found", "invalid_request_error");
    }

    @ExceptionHandler({UnknownModelException.class})
    public ResponseEntity<OpenAiErrorResponse> handleUnknownModel(UnknownModelException ex) {
        return openAi(400, "unknown_model", "invalid_request_error");
    }

    @ExceptionHandler({SystemRoleNotAllowedException.class})
    public ResponseEntity<OpenAiErrorResponse> handleSystem(SystemRoleNotAllowedException ex) {
        return openAi(400, "system_role_not_allowed", "invalid_request_error");
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<OpenAiErrorResponse> handleIllegal(IllegalArgumentException ex) {
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
    public ResponseEntity<OpenAiErrorResponse> handleCatchAll(Exception ex) {
        return openAi(400, "invalid_request", "invalid_request_error");
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<OpenAiErrorResponse> handleQuota(QuotaExceededException ex) {
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new OpenAiErrorResponse(new OpenAiErrorResponse.Error("quota_exceeded", "invalid_request_error", null, "quota_exceeded")));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<OpenAiErrorResponse> handleBase(BaseException ex) {
        org.springframework.http.HttpStatus status = ex.getHttpStatus();
        int code = (status == null) ? 500 : status.value();
        String errCode = ex.getMessage() == null ? "error" : ex.getMessage();
        return ResponseEntity.status(code).body(new OpenAiErrorResponse(new OpenAiErrorResponse.Error(errCode, "invalid_request_error", null, errCode)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAiErrorResponse> handleGeneric(Exception ex) {
        return openAi(500, "internal_error", "server_error");
    }

    private ResponseEntity<OpenAiErrorResponse> openAi(int status, String code, String type) {
        OpenAiErrorResponse.Error err = new OpenAiErrorResponse.Error(code, type, null, code);
        return ResponseEntity.status(status).body(new OpenAiErrorResponse(err));
    }
}