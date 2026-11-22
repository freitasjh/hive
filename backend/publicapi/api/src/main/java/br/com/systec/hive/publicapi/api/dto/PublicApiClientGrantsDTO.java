package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public class PublicApiClientGrantsDTO {

    @NotNull
    private List<String> grants;

    public List<String> getGrants() { return grants; }
    public void setGrants(List<String> grants) { this.grants = grants; }
}
