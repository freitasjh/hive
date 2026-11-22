package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Vinculo de prompt de um client público, com pin opcional. */
@JsonIgnoreProperties(ignoreUnknown = false)
public class PublicApiClientPromptBindingDTO {

    @NotBlank
    private String promptCode;

    @NotBlank
    @Pattern(regexp = "FOLLOW_ACTIVE|PINNED")
    private String pinMode = "FOLLOW_ACTIVE";

    private Integer pinnedVersion;

    public PublicApiClientPromptBindingDTO() {
    }

    public PublicApiClientPromptBindingDTO(String promptCode, String pinMode, Integer pinnedVersion) {
        this.promptCode = promptCode;
        this.pinMode = pinMode;
        this.pinnedVersion = pinnedVersion;
    }

    public String getPromptCode() { return promptCode; }
    public void setPromptCode(String promptCode) { this.promptCode = promptCode; }
    public String getPinMode() { return pinMode; }
    public void setPinMode(String pinMode) { this.pinMode = pinMode; }
    public Integer getPinnedVersion() { return pinnedVersion; }
    public void setPinnedVersion(Integer pinnedVersion) { this.pinnedVersion = pinnedVersion; }
}
