package br.com.systec.hive.publicapi.web.internal.v1.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ChatCompletionRequestDTO {

    @JsonProperty("model")
    private String model;

    @NotBlank
    @Size(min = 1, max = 255)
    @Pattern(regexp = "[A-Za-z0-9._-]+")
    @JsonProperty("prompt_code")
    private String prompt_code;

    @NotNull
    @Size(min = 1, max = 200)
    @Valid
    private List<ChatMessageDTO> messages;

    @Size(max = 128)
    private String user;

    private Boolean stream;

    // accepted and ignored
    @JsonIgnore private Object temperature;
    @JsonIgnore private Object max_tokens;
    @JsonIgnore private Object max_completion_tokens;
    @JsonIgnore private Object top_p;
    @JsonIgnore private Object top_k;
    @JsonIgnore private Object n;
    @JsonIgnore private Object logprobs;
    @JsonIgnore private Object top_logprobs;
    @JsonIgnore private Object presence_penalty;
    @JsonIgnore private Object frequency_penalty;
    @JsonIgnore private Object stop;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getPrompt_code() { return prompt_code; }
    public void setPrompt_code(String prompt_code) { this.prompt_code = prompt_code; }
    public List<ChatMessageDTO> getMessages() { return messages; }
    public void setMessages(List<ChatMessageDTO> messages) { this.messages = messages; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public Boolean getStream() { return stream; }
    public void setStream(Boolean stream) { this.stream = stream; }

    public static class ChatMessageDTO {
        @NotBlank
        @Pattern(regexp = "user|assistant")
        private String role;

        @NotBlank
        @Size(min = 1, max = 32000)
        private String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}