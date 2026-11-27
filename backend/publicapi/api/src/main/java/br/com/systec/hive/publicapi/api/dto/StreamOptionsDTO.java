package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code stream_options}: only {@code include_usage} is validated. Future fields are ignored
 * (ADR-002 E3/F5) instead of turning a compatible client's request into a 400.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamOptionsDTO {

    @JsonProperty("include_usage")
    private Boolean includeUsage;

    @JsonIgnore
    private boolean unknown = false;

    public Boolean getIncludeUsage() { return includeUsage; }
    public void setIncludeUsage(Boolean includeUsage) { this.includeUsage = includeUsage; }
    public boolean isUnknown() { return unknown; }
    public void setUnknown(boolean unknown) { this.unknown = unknown; }

    /** {@code true} only when the client explicitly asked for the terminal usage chunk. */
    public boolean includeUsage() {
        return Boolean.TRUE.equals(includeUsage);
    }
}
