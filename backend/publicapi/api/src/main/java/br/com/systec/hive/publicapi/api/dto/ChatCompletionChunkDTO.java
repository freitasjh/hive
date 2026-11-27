package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI {@code chat.completion.chunk} for the SSE wire (ADR-002 §4.1). The outer object always
 * carries {@code usage:null}/{@code finish_reason:null} where the schema expects them; {@code delta}
 * omits the channel that did not arrive, so a reasoning chunk never carries a {@code content} key.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChatCompletionChunkDTO {

    private String id;
    private String object;
    private long created;
    private String model;
    private List<Choice> choices;
    private ChatCompletionUsage usage;

    public ChatCompletionChunkDTO() {
    }

    public ChatCompletionChunkDTO(String id, String object, long created, String model,
                                  List<Choice> choices, ChatCompletionUsage usage) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Choice> getChoices() { return choices; }
    public void setChoices(List<Choice> choices) { this.choices = choices; }
    public ChatCompletionUsage getUsage() { return usage; }
    public void setUsage(ChatCompletionUsage usage) { this.usage = usage; }

    public static class Choice {
        private int index;
        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;

        public Choice() {
        }

        public Choice(int index, Delta delta, String finishReason) {
            this.index = index;
            this.delta = delta;
            this.finishReason = finishReason;
        }

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public Delta getDelta() { return delta; }
        public void setDelta(Delta delta) { this.delta = delta; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Delta {
        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        public Delta() {
        }

        public static Delta reasoning(String text) {
            Delta delta = new Delta();
            delta.reasoningContent = text;
            return delta;
        }

        public static Delta content(String text) {
            Delta delta = new Delta();
            delta.content = text;
            return delta;
        }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getReasoningContent() { return reasoningContent; }
        public void setReasoningContent(String reasoningContent) { this.reasoningContent = reasoningContent; }
    }
}
