package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Envelope de erro OpenAI preservado: {@code {"error":{"message","type","param","code"}}}.
 *
 * <p>{@code param} permanece serializado como {@code null} quando ausente — comportamento
 * congelado pela caracterização CT04. Nenhum código de status é decidido por este DTO: os
 * handlers locais dos controllers continuam sendo a única fonte de status/código.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "OpenAiError", description = "OpenAI-compatible error envelope: message, type, param and code.")
public class OpenAiError {

    private Error error;

    public OpenAiError() {
    }

    public OpenAiError(Error error) {
        this.error = error;
    }

    public Error getError() { return error; }
    public void setError(Error error) { this.error = error; }

    @Schema(name = "OpenAiErrorDetail")
    public static class Error {
        private String message;
        private String type;
        private String param;
        private String code;

        public Error() {
        }

        public Error(String message, String type, String param, String code) {
            this.message = message;
            this.type = type;
            this.param = param;
            this.code = code;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getParam() { return param; }
        public void setParam(String param) { this.param = param; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }
}
