package br.com.systec.hive.publicapi.web.internal.v1.dto;

public class TokenResponseDTO {
    private String access_token;
    private String token_type = "Bearer";
    private long expires_in;
    private String scope;

    public TokenResponseDTO() {}
    public TokenResponseDTO(String access_token, long expires_in, String scope) {
        this.access_token = access_token;
        this.expires_in = expires_in;
        this.scope = scope;
    }

    public String getAccess_token() { return access_token; }
    public void setAccess_token(String access_token) { this.access_token = access_token; }
    public String getToken_type() { return token_type; }
    public void setToken_type(String token_type) { this.token_type = token_type; }
    public long getExpires_in() { return expires_in; }
    public void setExpires_in(long expires_in) { this.expires_in = expires_in; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
