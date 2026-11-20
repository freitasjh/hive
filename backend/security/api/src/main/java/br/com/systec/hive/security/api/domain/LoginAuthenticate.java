package br.com.systec.hive.security.api.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

public class LoginAuthenticate implements Serializable {

    @Serial
    private static final long serialVersionUID = -392047392326032573L;
    private String accessToken;
    private String type;
    private String role;
    private UUID userId;
    private final String refreshToken;

    /**
     * Sinal de onboarding MFA: {@code true} quando o login não encontrou segredo habilitado
     * e o cliente pode convidar o usuário a configurar MFA. Não carrega segredo nem estado
     * detalhado do desafio — apenas este booleano derivado no ponto de login.
     *
     * <p>Caminho mínimo de propagação: o serviço preenche este campo no autenticador já
     * emitido e o mapper de web o copia para {@code LoginResponseDTO}. O ramo de desafio
     * nunca produz {@code LoginAuthenticate}, portanto nunca expõe este sinal.
     */
    private boolean mfaSetupAvailable;

    public LoginAuthenticate(UUID userId, String accessToken, String bearer, String role, String refreshToken) {
        this.userId = userId;
        this.accessToken = accessToken;
        this.type = bearer;
        this.role = role;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public boolean isMfaSetupAvailable() {
        return mfaSetupAvailable;
    }

    public void setMfaSetupAvailable(boolean mfaSetupAvailable) {
        this.mfaSetupAvailable = mfaSetupAvailable;
    }
}
