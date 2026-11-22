package br.com.systec.hive.publicapi.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Resposta one-shot de criação: o plaintext do secret aparece AQUI e nunca mais. */
public class PublicApiClientCreatedDTO extends PublicApiClientResponseDTO {

    @JsonProperty("client_secret")
    private String clientSecret;

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
}
