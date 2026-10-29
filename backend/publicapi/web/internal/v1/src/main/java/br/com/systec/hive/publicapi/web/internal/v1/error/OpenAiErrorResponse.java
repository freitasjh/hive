package br.com.systec.hive.publicapi.web.internal.v1.error;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiErrorResponse {
    public static class Error {
        private String message;
        private String type;
        private String param;
        private String code;

        public Error() {}
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

    private Error error;

    public OpenAiErrorResponse() {}
    public OpenAiErrorResponse(Error error) { this.error = error; }

    public Error getError() { return error; }
    public void setError(Error error) { this.error = error; }
}
