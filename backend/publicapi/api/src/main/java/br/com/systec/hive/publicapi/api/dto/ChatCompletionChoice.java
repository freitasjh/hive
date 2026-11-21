package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChatCompletionChoice")
public class ChatCompletionChoice {

    private int index;
    private ChatCompletionMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;

    public ChatCompletionChoice() {
    }

    public ChatCompletionChoice(int index, ChatCompletionMessage message, String finishReason) {
        this.index = index;
        this.message = message;
        this.finishReason = finishReason;
    }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }
    public ChatCompletionMessage getMessage() { return message; }
    public void setMessage(ChatCompletionMessage message) { this.message = message; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
}
