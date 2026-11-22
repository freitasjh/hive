package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class PublicApiClientPromptsDTO {

    @NotNull
    @Valid
    private List<PublicApiClientPromptBindingDTO> prompts;

    public List<PublicApiClientPromptBindingDTO> getPrompts() { return prompts; }
    public void setPrompts(List<PublicApiClientPromptBindingDTO> prompts) { this.prompts = prompts; }
}
