package br.com.systec.hive.publicapi.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatCompletionMessage")
public class ChatCompletionMessage {

    private String role;
    private String content;

    public ChatCompletionMessage() {
    }

    public ChatCompletionMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
