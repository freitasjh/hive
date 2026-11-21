package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response compatível com {@code POST /v1/chat/completions} da OpenAI.
 *
 * <p>{@code prompt_code} e {@code metadata} são extensões Hive explícitas — não campos canônicos
 * OpenAI. A serialização preserva o contrato anteriormente montado como {@code Map}: nomes
 * snake_case, {@code choices}, {@code usage} e {@code metadata.hive}.
 */
@Schema(name = "ChatCompletionResponse", description = "OpenAI-compatible chat completion response with Hive extensions.")
public class ChatCompletionResponse {

    private String id;
    private String object;
    private long created;
    private String model;
    private List<ChatCompletionChoice> choices;
    private ChatCompletionUsage usage;

    @JsonProperty("prompt_code")
    @Schema(description = "Hive extension: code of the prompt template that produced the completion.")
    private String promptCode;

    @Schema(description = "Hive extension: tenant metadata exposed under metadata.hive.")
    private ChatCompletionMetadata metadata;

    public ChatCompletionResponse() {
    }

    public ChatCompletionResponse(String id, String object, long created, String model,
                                  List<ChatCompletionChoice> choices, ChatCompletionUsage usage,
                                  String promptCode, ChatCompletionMetadata metadata) {
        this.id = id;
        this.object = object;
        this.created = created;
        this.model = model;
        this.choices = choices;
        this.usage = usage;
        this.promptCode = promptCode;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObject() { return object; }
    public void setObject(String object) { this.object = object; }
    public long getCreated() { return created; }
    public void setCreated(long created) { this.created = created; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<ChatCompletionChoice> getChoices() { return choices; }
    public void setChoices(List<ChatCompletionChoice> choices) { this.choices = choices; }
    public ChatCompletionUsage getUsage() { return usage; }
    public void setUsage(ChatCompletionUsage usage) { this.usage = usage; }
    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public ChatCompletionMetadata getMetadata() { return metadata; }
    public void setMetadata(ChatCompletionMetadata metadata) { this.metadata = metadata; }
}
